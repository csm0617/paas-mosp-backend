package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareBackupRepository;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareBackupExportEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareBackupFormEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareBackupPortVO;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.domain.software.service.ISoftwareBackupService;
import com.csm.paas.core.domain.whitelist.adapter.repository.IWhiteListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 软件整包备份/还原实现（规格：厂商 `SoftwareService.exportSoftwareForms:780-1020` 与
 * `importSoftwareInfo:1195-1382`，逐条出处见 {@link ISoftwareBackupService}）。
 *
 * <p><b>两处照抄的厂商缺陷</b>：
 * <ol>
 *   <li>`serviceKeyAndName` 的 fr_FR 分支判的是 **nameUs 是否非空**（而不是 nameFr）——
 *       即 en_US 没覆盖时 fr_FR 也不写；照抄；</li>
 *   <li>`importSoftwareInfo` 对已存在 key 会 `setCreatedAt(now)` 并整行 update（把创建时间改掉）；照抄。</li>
 * </ol>
 *
 * <p><b>一处主动偏离</b>：厂商 `relateServices` 会在关联服务时**从 K8s 容器的端口声明重新推导
 * `g_service_port` 行**；本重写改为**回放导出文件里带的 `servicePortList`**（更贴近"还原"语义，
 * 且不依赖 K8s 读端口），因此没有移植那段容器端口推导。其余关联/删除口径照抄。
 *
 * <p><b>类 C</b>：Deployment/Service 的 create-or-replace 与镜像 host 替换依赖集群与 registry。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareBackupServiceImpl implements ISoftwareBackupService {

    private final ISoftwareBackupRepository softwareBackupRepository;
    private final ISoftwareRepository softwareRepository;
    private final IServiceRuntimePort serviceRuntimePort;
    private final IRoleRepository roleRepository;
    private final ISettingRepository settingRepository;
    private final IWhiteListRepository whiteListRepository;
    private final IServiceQueryService serviceQueryService;
    private final IDockerRegistryPort dockerRegistryPort;
    private final IGatewayRefreshPort gatewayRefreshPort;

    /** 厂商 `gaop.k8s.namespace` / 常量 `K8s.NAMESPACE`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    // ================================================================ 导出

    @Override
    public SoftwareBackupExportEntity exportSoftwareRelation(List<String> softwareIds) {
        boolean type1 = softwareIds == null || softwareIds.isEmpty();
        List<String> idList = type1 ? List.of() : new ArrayList<>(new LinkedHashSet<>(softwareIds));
        List<SoftwareEntity> softwares = type1
                ? softwareBackupRepository.queryAll()
                : softwareBackupRepository.queryByIds(idList);

        List<Map<String, Object>> deployments;
        List<Map<String, Object>> services;
        if (type1) {
            deployments = serviceRuntimePort.listSoftwareExportDeployments(namespace);
            services = serviceRuntimePort.listSoftwareExportServices(namespace);
        } else {
            // 厂商 type=2：relationServiceList 是这些软件关联的全部服务名（DB 的 IN 查询结果）
            LinkedHashSet<String> serviceNames = new LinkedHashSet<>();
            for (SoftwareEntity software : softwares) {
                serviceNames.addAll(softwareRepository.queryAppNamesBySoftwareId(software.getId()));
            }
            List<String> names = new ArrayList<>(serviceNames);
            deployments = serviceRuntimePort.readSoftwareExportDeployments(namespace, names);
            services = serviceRuntimePort.readSoftwareExportServices(namespace, names);
        }

        List<SoftwareBackupFormEntity> forms = new ArrayList<>();
        for (SoftwareEntity software : softwares) {
            forms.add(toExportForm(software));
        }
        return SoftwareBackupExportEntity.builder()
                .softwareFormList(forms)
                .deploymentList(deployments)
                .serviceList(services)
                .whitelistPaths(queryWhitelistPaths())
                .serviceKeyAndName(queryServiceKeyAndName(deployments))
                .build();
    }

    /** 厂商 `setSoftwareInfo:986-1020` 的软件部分（含 roleListKey/serviceList/webEntry/servicePortList/nameUs/nameFr）。 */
    private SoftwareBackupFormEntity toExportForm(SoftwareEntity software) {
        SoftwareBackupFormEntity form = SoftwareBackupFormEntity.builder()
                .key(software.getKey())
                .name(software.getName())
                .type(software.getType())
                .routerKey(software.getRouterKey())
                .desc(software.getDesc())
                .location(software.getLocation())
                .remoteIp(software.getRemoteIp())
                .remoteName(software.getRemoteName())
                .remotePassword(software.getRemotePassword())
                .framework(software.getFramework())
                .execName(software.getExecName())
                .icon(software.getIcon())
                .roleListKey(softwareBackupRepository.queryRoleKeysBySoftwareId(software.getId()))
                .serviceList(new ArrayList<>(softwareRepository.queryAppNamesBySoftwareId(software.getId())))
                .build();
        // 厂商：screenshot 非空白才拆成列表，否则保持 null
        if (StringUtils.hasText(software.getScreenshot())) {
            form.setScreenShotList(Arrays.asList(software.getScreenshot().split(",")));
        }
        // 厂商：web_entry = true 的第一行 ⇒ webEntryName / webEntryPort
        softwareBackupRepository.queryFirstWebEntry(software.getId()).ifPresent(entry -> {
            form.setWebEntryName(entry.getServiceName());
            form.setWebEntryPort(entry.getPort());
        });
        List<SoftwareBackupPortVO> ports = softwareBackupRepository.queryServicePorts(software.getId());
        if (!ports.isEmpty()) {
            form.setServicePortList(ports);
        }
        form.setNameUs(queryAutoName("software", "en_US", software.getKey()));
        form.setNameFr(queryAutoName("software", "fr_FR", software.getKey()));
        return form;
    }

    /** 厂商 `getWhitelistPaths:824-827`：全部 `g_white_list.path`。 */
    private List<String> queryWhitelistPaths() {
        return whiteListRepository.queryPage(null, 0, Integer.MAX_VALUE).stream()
                .map(w -> w.getPath()).toList();
    }

    /**
     * 厂商 `exportSoftwareForms:794-816`：对 `deploymentList` 里每个名字取
     * `i18n.service.{en_US,fr_FR}.&lt;name&gt;`。
     * <p>照抄厂商缺陷：fr_FR 分支判的是 **nameUs 是否非空**。
     */
    private Map<String, String> queryServiceKeyAndName(List<Map<String, Object>> deployments) {
        Map<String, String> serviceKeyAndName = new java.util.LinkedHashMap<>();
        for (Map<String, Object> deployment : deployments) {
            String name = metadataName(deployment);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String settingKeyUs = "i18n.service.en_US." + name;
            String nameUs = queryAutoName("service", "en_US", name);
            if (StringUtils.hasText(nameUs)) {
                serviceKeyAndName.put(settingKeyUs, nameUs);
            }
            String settingKeyFr = "i18n.service.fr_FR." + name;
            String nameFr = queryAutoName("service", "fr_FR", name);
            if (StringUtils.hasText(nameUs)) {
                serviceKeyAndName.put(settingKeyFr, nameFr);
            }
        }
        return serviceKeyAndName;
    }

    private String queryAutoName(String module, String language, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return settingRepository.queryValue("i18n." + module + "." + language + "." + key).orElse(null);
    }

    private static String metadataName(Map<String, Object> snapshot) {
        Object metadata = snapshot == null ? null : snapshot.get("metadata");
        if (!(metadata instanceof Map<?, ?> map)) {
            return null;
        }
        Object name = map.get("name");
        return name == null ? null : name.toString();
    }

    // ================================================================ 导入

    @Override
    public void importSoftwareRelation(SoftwareBackupExportEntity entity) {
        if (entity == null) {
            return;
        }
        long timeMillis = System.currentTimeMillis();
        List<SoftwareBackupFormEntity> forms = entity.getSoftwareFormList();
        if (forms != null && !forms.isEmpty()) {
            for (SoftwareBackupFormEntity form : forms) {
                upsertSoftware(form, timeMillis);
            }
        }
        // 厂商：Deployment / Service 先还原，再做软件与服务的关联
        List<Map<String, Object>> deployments = entity.getDeploymentList();
        if (deployments != null) {
            String registryHost = dockerRegistryPort.registryHost();
            for (Map<String, Object> deployment : deployments) {
                serviceRuntimePort.createOrReplaceSoftwareDeployment(namespace, deployment, registryHost);
            }
        }
        List<Map<String, Object>> services = entity.getServiceList();
        if (services != null) {
            for (Map<String, Object> service : services) {
                serviceRuntimePort.createOrReplaceService(namespace, service);
            }
        }
        if (forms != null) {
            for (SoftwareBackupFormEntity form : forms) {
                softwareBackupRepository.queryByKey(form.getKey()).ifPresent(software ->
                        restoreRelations(software, form, timeMillis));
            }
        }
        restoreWhitelist(entity.getWhitelistPaths());
        if (entity.getServiceKeyAndName() != null) {
            entity.getServiceKeyAndName().forEach((key, val) -> {
                if (StringUtils.hasText(key) && val != null) {
                    settingRepository.upsertAutoName(key, val);
                }
            });
        }
        gatewayRefreshPort.refresh();
    }

    /** 厂商 `importSoftwareInfo:1199-1250`：key 已存在覆盖，否则新建。 */
    private void upsertSoftware(SoftwareBackupFormEntity form, long timeMillis) {
        String screenshot = joinScreenshot(form.getScreenShotList());
        boolean existKey = softwareRepository.existsKey(form.getKey());
        if (existKey) {
            SoftwareEntity existing = softwareBackupRepository.queryByKey(form.getKey())
                    .orElseGet(SoftwareEntity::new);
            applyForm(form, existing);
            existing.setName(form.getName());
            existing.setKey(form.getKey());
            existing.setCreatedAt(timeMillis);
            existing.setOrderTop(0);
            existing.setDesc(form.getDesc());
            existing.setScreenshot(screenshot);
            softwareBackupRepository.updateSoftwareForImport(existing);
        } else {
            SoftwareEntity created = SoftwareEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .createdAt(timeMillis)
                    .orderTop(0)
                    .screenshot(screenshot)
                    .build();
            applyForm(form, created);
            created.setName(form.getName());
            created.setKey(form.getKey());
            created.setDesc(form.getDesc());
            softwareRepository.insert(created);
        }
        saveAutoName("software", "en_US", form.getKey(), form.getNameUs());
        saveAutoName("software", "fr_FR", form.getKey(), form.getNameFr());
    }

    /** 厂商 `BeanUtils.copyProperties(softwareForm, sf)` 覆盖的那些列（不含 id/时间/orderTop/screenshot）。 */
    private static void applyForm(SoftwareBackupFormEntity form, SoftwareEntity software) {
        software.setKey(form.getKey());
        software.setName(form.getName());
        software.setType(form.getType());
        software.setRouterKey(form.getRouterKey());
        software.setDesc(form.getDesc());
        software.setLocation(form.getLocation());
        software.setRemoteIp(form.getRemoteIp());
        software.setRemoteName(form.getRemoteName());
        software.setRemotePassword(form.getRemotePassword());
        software.setFramework(form.getFramework());
        software.setExecName(form.getExecName());
        software.setIcon(form.getIcon());
    }

    /** 厂商 `saveAutoNameMessage`：key 空白不写；val 为 null 不写（空串会写）。 */
    private void saveAutoName(String module, String language, String key, String val) {
        if (!StringUtils.hasText(key) || val == null) {
            return;
        }
        settingRepository.upsertAutoName("i18n." + module + "." + language + "." + key, val);
    }

    private static String joinScreenshot(List<String> screenShotList) {
        if (screenShotList == null || screenShotList.isEmpty()) {
            return null;
        }
        return String.join(",", screenShotList);
    }

    /**
     * 厂商 `importSoftwareInfo:1332-1382` 的"关联软件与服务"那段：
     * 角色按 key 解析 id（解不到的跳过）→ 关联服务（补/删 `g_software_app`）→
     * 还原端口行 → `webEntry` 置位 → 最后按 form 的 `webEntryName/webEntryPort` 再置一次。
     */
    private void restoreRelations(SoftwareEntity software, SoftwareBackupFormEntity form, long timeMillis) {
        List<String> roleKeys = form.getRoleListKey() == null ? List.of() : form.getRoleListKey();
        if (!roleKeys.isEmpty()) {
            List<String> roleIds = roleRepository.queryRoleIdsByKeys(roleKeys);
            softwareRepository.relateRoles(software.getId(), roleIds);
        }
        relateServices(software.getId(), form.getServiceList(), timeMillis);
        List<SoftwareBackupPortVO> ports = form.getServicePortList();
        if (ports != null) {
            for (SoftwareBackupPortVO port : ports) {
                softwareBackupRepository.upsertServicePort(software.getId(), port.getServiceName(),
                        port.getPort() == null ? 0 : port.getPort(), port.getPathName(),
                        port.getWebEntry(), timeMillis);
                if (Boolean.TRUE.equals(port.getWebEntry())) {
                    serviceQueryService.setServiceEntry(namespace, software.getId(),
                            port.getServiceName(), port.getPort(), 1);
                }
            }
        }
        if (StringUtils.hasText(form.getWebEntryName()) && form.getWebEntryPort() != null) {
            serviceQueryService.setServiceEntry(namespace, software.getId(),
                    form.getWebEntryName(), form.getWebEntryPort(), 1);
        }
    }

    /**
     * 厂商 `relateServices:2092-2127` 的 `g_software_app` 部分：
     * 列表里已有的服务不重复插，列表外的关联删掉（**端口行的重建本重写改为回放导出数据**）。
     */
    private void relateServices(String softwareId, List<String> serviceList, long timeMillis) {
        if (serviceList == null) {
            return;
        }
        List<String> existing = softwareRepository.queryAppNamesBySoftwareId(softwareId);
        List<String> toDelete = existing.stream().filter(name -> !serviceList.contains(name)).toList();
        if (!toDelete.isEmpty()) {
            softwareBackupRepository.deleteSoftwareAppsByNames(softwareId, toDelete);
        }
        for (String serviceName : serviceList) {
            if (!existing.contains(serviceName)) {
                softwareBackupRepository.insertSoftwareApp(softwareId, serviceName, timeMillis);
            }
        }
    }

    /** 厂商 `whiteListService.backupWhiteLists:87-100`：不存在才插（createdAt = now）。 */
    private void restoreWhitelist(List<String> whitelistPaths) {
        if (whitelistPaths == null || whitelistPaths.isEmpty()) {
            return;
        }
        for (String path : whitelistPaths) {
            if (whiteListRepository.queryByPath(path).isPresent()) {
                continue;
            }
            whiteListRepository.insert(path, System.currentTimeMillis());
        }
    }
}
