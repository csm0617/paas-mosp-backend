package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.valobj.GaopServiceFilter;
import com.csm.paas.core.domain.software.valobj.GaopServiceNames;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 只读领域服务：把运行态服务聚合成列表。
 * <p>注意 {@code runningDeployment} 的语义在厂商侧尚未确认（运行时样本里 9 个服务、5 个 Pod 在跑，
 * 厂商却给 0）。这里先用"就绪副本 ≥ 1"作为工作定义，并把差异记进工单 06 的结论。
 */
@Slf4j
@Service
public class ServiceQueryServiceImpl implements IServiceQueryService {

    @Resource
    private IServiceRuntimePort serviceRuntimePort;

    @Resource
    private IServiceAuthRepository serviceAuthRepository;

    /** Pod 重启记录来自 `g_pod_event`（软件域仓储里已有的 dao 也能查，但归属放这里更清晰）。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository podEventRepository;

    /** i18n 自动名落在 `g_setting`（键形如 `i18n.service.<lang>.<serviceKey>`）。 */
    @Resource
    private com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository i18nRepository;

    @Override
    public boolean updateServiceName(String namespace, String serviceKey, String name, String nameUs, String nameFr) {
        try {
            serviceRuntimePort.patchDeploymentServiceLabel(namespace, serviceKey, name);
        } catch (Exception e) {
            log.error("修改服务名称失败（Deployment），回退 StatefulSet", e);
            try {
                serviceRuntimePort.patchStatefulSetServiceLabel(namespace, serviceKey, name);
            } catch (Exception ex) {
                throw new com.csm.paas.core.types.exception.SystemException("service.modify.name.failed");
            }
        }
        // 厂商两条路径成功时都写 i18n 自动名（`g_setting['i18n.service.<lang>.<key>']`）
        i18nRepository.upsertValue("i18n.service.en_US." + serviceKey, nameUs, "service name en_US");
        i18nRepository.upsertValue("i18n.service.fr_FR." + serviceKey, nameFr, "service name fr_FR");
        return true;
    }

    @Override
    public ServiceListEntity queryGaopServiceList(String namespace, int page, int size, Integer type, String keyword) {
        List<ServiceRuntimeEntity> all = serviceRuntimePort.queryServices(namespace).stream()
                .filter(s -> GaopServiceFilter.matches(type, s.getName(), s.getAppType(), s.getAppName()))
                .peek(s -> s.setFixedName(GaopServiceNames.fixedName(s.getName())))
                .collect(Collectors.toList());

        if (keyword != null) {
            // 厂商两段式：先按 deployment 名 contains；若为空，再用 filterServiceName 兜底一次
            List<ServiceRuntimeEntity> byName = all.stream()
                    .filter(s -> s.getName() != null && s.getName().contains(keyword))
                    .collect(Collectors.toList());
            all = byName.isEmpty() ? all.stream().filter(s -> filterServiceName(keyword, s)).collect(Collectors.toList()) : byName;
        }

        int total = all.size();
        int from = Math.max(0, com.csm.paas.core.types.util.PageUtils.offset(page, size));
        int to = Math.min(total, from + size);
        List<ServiceRuntimeEntity> pageItems = from >= total ? List.of() : all.subList(from, to);

        int running = (int) pageItems.stream().filter(s -> s.getReplicas() != null && s.getReplicas() > 0).count();
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(total)
                .count(pageItems.size())
                .services(pageItems)
                .build();
    }

    /**
     * 厂商 `ServiceController.filterServiceName:2070-2081` 的**精确实现**：
     * 取 pod 模板注解 `gaop/serviceLabel`，**大小写敏感的 contains**；注解或该键缺失 → false。
     * <p>注意它不是匹配 i18n 名、也不是匹配 `fixedName`——第一版我按"名字/显示名忽略大小写包含"实现，
     * 是**错的**（`findings/56`）。
     */
    private static boolean filterServiceName(String keyword, ServiceRuntimeEntity s) {
        String label = s.getServiceLabel();
        return label != null && label.contains(keyword);
    }

    @Override
    public boolean updateServiceCommand(String namespace, String serviceName,
                                        List<String> command, List<String> args) {
        if (!serviceRuntimePort.patchDeploymentCommand(namespace, serviceName, command, args)) {
            // 厂商：读不到 Deployment → WrongArgumentException（**没有** StatefulSet 回退）
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.service.name.not.found", serviceName);
        }
        return true;
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO> queryRestartInfo(String serviceName) {
        // 厂商用 serviceName 作为**请求参数**（不是 body/路径）；查库在仓储里，这里只做归属
        return podEventRepository.queryPodRestartInfo(serviceName);
    }

    @Override
    public boolean cancelServiceStartup(String namespace, List<String> serviceNames) {
        for (String serviceName : serviceNames) {
            if (!serviceRuntimePort.cancelDeploymentStartup(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.get.service.config.exception.cancel.delay", serviceName);
            }
        }
        return true;
    }

    @Override
    public boolean setServiceStartup(String namespace, List<String> serviceNames, long time,
                                     String harborHost, String initImage) {
        if (time < 0L) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.negative.startup.delay");
        }
        for (String serviceName : serviceNames) {
            if (!serviceRuntimePort.setDeploymentStartup(namespace, serviceName, time, harborHost, initImage)) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.get.service.config.exception.set.delay", serviceName);
            }
        }
        return true;
    }

    @Override
    public boolean updateServiceNodePort(String namespace, String serviceName, String type,
                                         List<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO> nodePorts) {
        String effectiveType = (type == null || type.isBlank()) ? "ClusterIP" : type;   // 厂商默认值
        List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> specs = new ArrayList<>();

        if ("NodePort".equals(effectiveType) && (nodePorts == null || nodePorts.isEmpty())) {
            // A 分支：从 Deployment 的第一个容器端口推导
            var containerPorts = serviceRuntimePort.readDeploymentContainerPorts(namespace, serviceName);
            if (containerPorts == null) {
                // 厂商：消息本身就是参数（带服务名的中文），走 SystemException（500/-1）
                throw new com.csm.paas.core.types.exception.SystemException(
                        "设置NodePort失败：该服务" + serviceName + "无法获取deployment信息");
            }
            if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.SystemException("设置NodePort失败：service为空");
            }
            if (containerPorts.isEmpty()) {
                return false;   // 厂商：ports 为空 → 什么都不做，v1Service 为 null ⇒ data:false
            }
            for (int i = 0; i < containerPorts.size(); i++) {
                var cp = containerPorts.get(i);
                specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                        .appProtocol(cp.getProtocol())
                        .name((cp.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                        .port(cp.getContainerPort())
                        .protocol(cp.getProtocol())
                        .targetPort(cp.getContainerPort())
                        .build());
            }
        } else {
            // B 分支：按入参构建
            if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.SystemException("设置NodePort失败：service为空");
            }
            if ("NodePort".equals(effectiveType) && nodePorts != null) {
                for (int i = 0; i < nodePorts.size(); i++) {
                    var np = nodePorts.get(i);
                    specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                            .appProtocol(np.getProtocol())
                            .name((np.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                            .nodePort(np.getNodePort())
                            .port(np.getPort())
                            .protocol(np.getProtocol())
                            .targetPort(np.getPort())
                            .build());
                }
            }
        }
        try {
            return serviceRuntimePort.patchServicePorts(namespace, serviceName, effectiveType, specs) != null;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("422") && msg.contains("provided port is already allocated")) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException("service.port.occupied");
            }
            throw new com.csm.paas.core.types.exception.SystemException("service.update.nodeport.failed");
        }
    }

    @Override
    public Map<String, List<Map<String, Object>>> queryOtherPluginWorkloads() {
        return serviceRuntimePort.listOtherPluginWorkloads();
    }

    @Override
    public java.util.Map<String, Object> scaleService(String namespace, String serviceName, int replicas) {
        java.util.Map<String, Object> patched = null;
        try {
            patched = serviceRuntimePort.patchDeploymentReplicas(namespace, serviceName, replicas);
        } catch (Exception e) {
            log.error("scale deployment error", e);
        }
        if (patched != null) {
            return patched;
        }
        try {
            java.util.Map<String, Object> statefulSet = serviceRuntimePort.patchStatefulSetReplicas(
                    namespace, serviceName, replicas);
            if (statefulSet != null) {
                return statefulSet;
            }
            throw new IllegalStateException("neither Deployment nor StatefulSet patched");
        } catch (Exception e) {
            // 厂商：两者都失败 → SystemException(异常消息) ⇒ 500 / code=-1
            throw new com.csm.paas.core.types.exception.SystemException(e.getMessage());
        }
    }

    @Override
    public boolean updateServicePriority(String namespace, String serviceName, String level) {
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            // 厂商：只读 Deployment，读不到就 400（**没有** StatefulSet 回退，与 hostName 端点不同）
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.change.priority.failed");
        }
        // 厂商：level 空白 → 设成**空串**（不是 null）
        serviceRuntimePort.patchDeploymentPriorityClassName(namespace, serviceName, level == null ? "" : level);
        return true;
    }

    @Override
    public boolean updateServiceHostname(String namespace, String serviceName, String hostName) {
        // 厂商：**读不到 Deployment**（ApiException）才回退；不是"hostname 为空"就回退
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            return updateStatefulSetHostname(namespace, serviceName, hostName);
        }
        String current = serviceRuntimePort.deploymentHostname(namespace, serviceName);
        if (isBlank(hostName) && isBlank(current)) {
            return true;   // 厂商：两边都空白 → 静默 no-op
        }
        serviceRuntimePort.patchDeploymentHostname(namespace, serviceName, hostName);
        return true;
    }

    private boolean updateStatefulSetHostname(String namespace, String serviceName, String hostName) {
        String current = serviceRuntimePort.statefulSetHostname(namespace, serviceName);
        if (current == null) {
            // 实测厂商文案：HTTP **400** + `service.not.found.update.host.pod`（不是 500、也不是我先前猜的键名）
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.not.found.update.host.pod", serviceName);
        }
        if (isBlank(hostName) && isBlank(current)) {
            return true;
        }
        serviceRuntimePort.patchStatefulSetHostname(namespace, serviceName, hostName);
        return true;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    @Override
    public ServiceListEntity queryServiceAuthList(String namespace, int page, int size, String keyword,
                                                 String authType, String serviceType) {
        // ① 基础过滤：appName 既不是 gaop 也不是 gis-server（与 gaop 端点互补！）
        List<ServiceRuntimeEntity> all = serviceRuntimePort.queryServices(namespace).stream()
                .filter(s -> !"gis-server".equals(s.getAppName()) && !"gaop".equals(s.getAppName()))
                .collect(Collectors.toList());

        // ② keyword 两段式（与 gaop 同款）
        if (keyword != null) {
            List<ServiceRuntimeEntity> byName = all.stream()
                    .filter(s -> s.getName() != null && s.getName().contains(keyword))
                    .collect(Collectors.toList());
            all = byName.isEmpty()
                    ? all.stream().filter(s -> filterServiceName(keyword, s)).collect(Collectors.toList())
                    : byName;
        }

        // ③ authType 过滤（仅在传了 authType 时执行）
        if (authType != null && !authType.isBlank() && !all.isEmpty()) {
            List<Integer> typeList = java.util.Arrays.stream(authType.split(","))
                    .map(String::trim).filter(v -> !v.isEmpty()).map(Integer::valueOf).toList();
            Map<String, ServiceAuthEntity> authByName = serviceAuthRepository.queryAll().stream()
                    .collect(Collectors.toMap(ServiceAuthEntity::getServiceId, Function.identity(), (a, b) -> a));
            all = all.stream().filter(s -> keepByAuthType(s, typeList, authByName.get(s.getName())))
                    .collect(Collectors.toList());
        }

        // ④ 分页 + 装配（与 /v1/service 同一套形状）
        int total = all.size();
        int from = Math.max(0, com.csm.paas.core.types.util.PageUtils.offset(page, size));
        int to = Math.min(total, from + size);
        List<ServiceRuntimeEntity> pageItems = from >= total ? List.of() : all.subList(from, to);
        int running = (int) pageItems.stream().filter(s -> s.getReplicas() != null && s.getReplicas() > 0).count();
        return ServiceListEntity.builder()
                .runningDeployment(running).total(total).count(pageItems.size()).services(pageItems).build();
    }

    /**
     * 厂商 `ServiceController:3524-3555` 的授权状态过滤（逐行照抄，第 2 条按等价式化简）。
     *
     * @param auth 该服务在 `g_service_auth` 里的记录，可能为 null
     */
    private static boolean keepByAuthType(ServiceRuntimeEntity s, List<Integer> typeList, ServiceAuthEntity auth) {
        if (auth != null) {
            return auth.getAuth() != null && typeList.contains(auth.getAuth());
        }
        String serviceTypeName = s.getServiceType();
        if (typeList.size() > 1 && typeList.contains(0) && typeList.contains(1)) {
            return true;
        }
        if (typeList.size() == 1 && typeList.contains(0)) {
            // 厂商原式：serviceTypeName == null || length != 1 || !"前台服务".equals(serviceTypeName)
            // 本分支下 length == 1 恒真 ⇒ 化简为下面这句
            return serviceTypeName == null || !"前台服务".equals(serviceTypeName);
        }
        return typeList.contains(1) && "前台服务".equals(serviceTypeName);
    }

    @Override
    public Map<String, List<String>> queryNodeLabels() {
        return serviceRuntimePort.queryNodeLabels();
    }

    @Override
    public ServiceListEntity queryServiceList(String namespace) {
        List<ServiceRuntimeEntity> services = serviceRuntimePort.queryServices(namespace);

        // 回填授权状态：厂商按服务名去 g_service_auth 取（表空则保持 null，与厂商一致）
        Map<String, ServiceAuthEntity> authByService = serviceAuthRepository.queryAll().stream()
                .collect(Collectors.toMap(ServiceAuthEntity::getServiceId, Function.identity(), (a, b) -> a));
        services.forEach(s -> s.setServiceAuth(authByService.get(s.getName())));
        int running = (int) services.stream()
                .filter(s -> s.getReplicas() != null && s.getReplicas() > 0)
                .count();
        log.info("查询服务列表 namespace:{} 共 {} 个，就绪 {} 个", namespace, services.size(), running);
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(services.size())
                .count(services.size())
                .services(services)
                .build();
    }
}
