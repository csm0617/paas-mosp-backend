package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareMessageEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import com.csm.paas.core.domain.software.service.ISoftwareQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 软件列表（厂商 `SoftwareService.getSoftwareList`，`SoftwareService.java:483-547`）。
 *
 * <p>每个软件行要额外算四样东西（这是它被称为"聚合端点"而不是"只读列表"的原因）：
 * <ol>
 *   <li>`releaseStatus`：白名单里有 `/<key>/**` → 1，否则 0；</li>
 *   <li>`roleList`：`g_role_software.app_id = 软件id` 反查战位（`{id,name,key}`）；**无关联时保持 null**（不是 []）；</li>
 *   <li>`web`：见 {@link #webStatus}；</li>
 *   <li>`url`：`CS` 类型（忽略大小写）拼 `gaop://cs?...`，否则用 `router_key`。
 *       拼串里含**远端账号/口令**（`remote_name`/`remote_password`），且 `host` 取自请求头 `x-forwarded-host`。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareQueryServiceImpl implements ISoftwareQueryService {

    private final ISoftwareRepository softwareRepository;
    private final IWebEntryStatusPort webEntryStatusPort;
    private final com.csm.paas.core.domain.licence.service.ILicenceQueryService licenceQueryService;

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO> queryAuthList(
            int page, int size, String name, String type) {
        String likeName = (name == null || name.isEmpty()) ? null : "%" + name + "%";
        long now = System.currentTimeMillis();
        java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO> list = new java.util.ArrayList<>();
        for (SoftwareEntity s : softwareRepository.queryAuthPage(likeName, type, PageUtils.offset(page, size), size)) {
            var info = com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO.builder()
                    .id(s.getId()).softWareName(s.getName()).softWareKey(s.getKey()).framework(s.getFramework())
                    .build();
            if (s.getActiveCode() == null || s.getActiveCode().isBlank()) {
                info.setStatus(0);
                info.setAuthCreateTime(null);
                info.setAuthExpireTime(null);
            } else {
                Long activeExpire = s.getActiveExpire();
                if (activeExpire != null && now > activeExpire) {
                    info.setStatus(2);
                } else if (activeExpire == null || activeExpire == 0L) {
                    info.setStatus(0);
                } else {
                    info.setStatus(1);
                }
                info.setAuthCreateTime(s.getActiveCreate());
                info.setAuthExpireTime(s.getActiveExpire());
            }
            list.add(info);
        }
        return list;
    }

    @Override
    public long countAuthList(String name, String type) {
        return softwareRepository.countAuth((name == null || name.isEmpty()) ? null : "%" + name + "%", type);
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> queryServicePorts(
            String softwareId, String serviceName) {
        return softwareRepository.queryServicePorts(softwareId, serviceName);
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.ServicePortVO updateServicePortPath(
            String softwareId, String serviceName, int port, String path) {
        // 厂商 controller 先校验 path，再交给 service；这里把 controller 的校验也放进域服务，保持"可被直接调用"的一致语义
        if (path == null || path.isBlank()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("software.path.must.not.null");
        }
        String p = path.trim();
        if (p.length() > 45 || p.length() < 3) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("software.path.length.invalid");
        }
        if (Character.isDigit(p.charAt(0))) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("software.path.can.not.start.by.num");
        }
        if (!p.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("software.path.must.in.char");
        }

        if (softwareRepository.existsServicePath(softwareId, serviceName, p)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "software.path.exist.in.service", serviceName);
        }
        var existing = softwareRepository.queryServicePort(softwareId, serviceName, port);
        if (existing == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.port.not.found", port);
        }
        return softwareRepository.updateServicePortPath(existing.getId(), p);
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.SoftwareIdentifierVO generateSoftwareIdentifier(
            java.util.List<String> softwareKeyList) {
        if (softwareKeyList == null || softwareKeyList.isEmpty()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("software.invalid.api.parameter");
        }
        return com.csm.paas.core.domain.software.model.valobj.SoftwareIdentifierVO.builder()
                .serviceIdList(softwareKeyList)
                .machineCode(licenceQueryService.machineCodeForSoftware())
                .build();
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO> cancelSoftwareAuth(
            java.util.List<String> softwareIdList) {
        long now = System.currentTimeMillis();
        java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO> results = new java.util.ArrayList<>();
        for (String id : softwareIdList) {
            var software = softwareRepository.queryById(id)
                    .orElseThrow(() -> new com.csm.paas.core.types.exception.WrongArgumentException(
                            "software.not.exist.or.deleted"));
            if (software.getActiveCode() == null || software.getActiveCode().isBlank()) {
                continue;   // 厂商：没有激活码就跳过，不产出结果项
            }
            softwareRepository.clearSoftwareAuth(id, now);
            results.add(com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO.builder()
                    .softwareKey(software.getKey()).result(false).build());
        }
        if (results.isEmpty()) {
            // 厂商：一个都没取消到 ⇒ 400（不是返回空数组）
            throw new com.csm.paas.core.types.exception.WrongArgumentException("auth.no.software.authorization");
        }
        return results;
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO> activateSoftware(
            String authToken) {
        long start = System.currentTimeMillis();
        var payload = licenceQueryService.verifySoftwareActivationCode(
                authToken == null ? null : authToken.replaceAll("\\s*|\\t|\\r|\\n", ""));
        String machineCodeBase64 = licenceQueryService.machineCodeForSoftware();
        if (!payload.getMachineCode().equals(machineCodeBase64)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("auth.machine.code.mismatch");
        }
        java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO> results = new java.util.ArrayList<>();
        for (String softwareId : payload.getServiceIdList()) {
            var software = softwareRepository.queryById(softwareId);
            if (software.isEmpty()) {
                continue;   // 厂商：软件不存在就跳过（不报错）
            }
            softwareRepository.saveSoftwareAuth(softwareId, start, authToken, payload.getExpiresAt());
            results.add(com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO.builder()
                    .softwareKey(software.get().getKey()).result(true).build());
        }
        return results;
    }

    @Override
    public SoftwareListEntity querySoftwareList(int page, int size, String name, String type,
                                                String order, String sort, String host) {
        String likeName = (name == null || name.isEmpty()) ? null : "%" + name + "%";
        boolean asc = "asc".equals(sort);
        String orderField = (order == null || order.isEmpty()) ? "key" : order;

        List<SoftwareEntity> rows = softwareRepository.queryPage(
                likeName, type, orderField, asc, PageUtils.offset(page, size), size);
        long total = softwareRepository.count(likeName, type);

        List<SoftwareMessageEntity> list = new ArrayList<>();
        if (!rows.isEmpty()) {
            Map<String, Integer> webStatus = queryWebStatusForAll(rows);
            for (SoftwareEntity s : rows) {
                list.add(toMessage(s, host, webStatus));
            }
        }
        log.debug("查询软件列表 page={} size={} name={} type={} 命中 {} 行", page, size, name, type, list.size());
        return SoftwareListEntity.builder().softwareList(list).total(total).build();
    }

    /** 先把本页所有软件用到的 service name 收集起来一次查完（厂商也是先取一次 Deployment 列表）。 */
    private Map<String, Integer> queryWebStatusForAll(List<SoftwareEntity> rows) {
        List<String> serviceNames = new ArrayList<>();
        for (SoftwareEntity s : rows) {
            List<String> entries = softwareRepository.queryWebEntryServiceNames(s.getId());
            if (!entries.isEmpty() && entries.get(0) != null) {
                serviceNames.add(entries.get(0));
            }
        }
        if (serviceNames.isEmpty()) {
            return Map.of();
        }
        return webEntryStatusPort.queryWebStatus(serviceNames);
    }

    private SoftwareMessageEntity toMessage(SoftwareEntity s, String host, Map<String, Integer> webStatus) {
        List<String> webEntries = softwareRepository.queryWebEntryServiceNames(s.getId());
        int web = webStatus(webEntries, webStatus);

        List<RelationRoleVO> roleList = null;
        List<String> roleIds = softwareRepository.queryRoleIdsBySoftwareId(s.getId());
        if (!CollectionUtils.isEmpty(roleIds)) {
            roleList = softwareRepository.queryRelationRoles(roleIds);
        }

        return SoftwareMessageEntity.builder()
                .software(s)
                .releaseStatus(softwareRepository.existsWhiteListPath("/" + s.getKey() + "/**") ? 1 : 0)
                .roleList(roleList)
                .web(web)
                .url(buildUrl(s, host))
                .build();
    }

    /**
     * 厂商 `SoftwareService.java:515-527`：无 web 入口行 → 0；有行但 Deployment 找不到 → 2；
     * 找到则用 {@link #deploymentStatusToWeb} 的结果。
     */
    private int webStatus(List<String> webEntries, Map<String, Integer> webStatus) {
        if (webEntries.isEmpty()) {
            return 0;
        }
        Integer status = webStatus.get(webEntries.get(0));
        return status == null ? 2 : status;
    }

    private String buildUrl(SoftwareEntity s, String host) {
        if (!"CS".equalsIgnoreCase(s.getType())) {
            return s.getRouterKey();
        }
        return "gaop://cs?name=" + nvl(s.getName()) + "&id=" + nvl(s.getId()) + "&key=" + nvl(s.getKey())
                + "&version=" + nvl(s.getVersion()) + "&execName=" + nvl(s.getExecName())
                + "&remoteIp=" + nvl(s.getRemoteIp()) + "&remoteName=" + nvl(s.getRemoteName())
                + "&remotePassword=" + nvl(s.getRemotePassword()) + "&host=" + nvl(host);
    }

    /** 厂商是字符串拼接（`+`），Java 里 null 会变成字面量 "null"。照抄这个行为。 */
    private static String nvl(String v) {
        return v;
    }
}
