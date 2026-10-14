package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.MappingInfoDTO;
import com.csm.paas.core.api.dto.ServiceAuthDTO;
import com.csm.paas.core.api.dto.ServiceItemDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域实体 → 厂商契约 DTO 的装配。
 * <p>抽出来是为了**可离线测试**：`ServiceListAssemblerTest` 用录制下来的厂商样本回放，
 * 断言"前端实际读取的字段路径"逐条一致（见 docs/findings/read-paths.json）。
 * <p>{@code ServiceItemDTO} 的 19 个键一个都不能少——前端可能读其中任何一个（实测其中 7 个不读，但保留成本为零）。
 */
public final class ServiceListAssembler {

    private ServiceListAssembler() {
    }

    public static ServiceListResponseDTO toResponse(ServiceListEntity entity) {
        List<ServiceItemDTO> items = new ArrayList<>();
        for (ServiceRuntimeEntity s : entity.getServices()) {
            items.add(ServiceItemDTO.builder()
                    .service(s.getServiceSnapshot())
                    .deployment(s.getDeploymentSnapshot())
                    .serviceAuth(toAuthDTO(s.getServiceAuth()))
                    // 厂商运行态恒为该三键对象（值可为 null），前端据此开映射面板
                    .mappingInfo(MappingInfoDTO.builder().build())
                    .hostIP(s.getHostIP())
                    .imageInfo(s.getImage())
                    .restartCount(s.getRestartCount())
                    // 只有 gaop 端点会设置 fixedName（GaopName 里的中文显示名）；
                    // 列表端点不设 ⇒ 回退服务名，保证已对拉通过的 /v1/service 行为不变
                    .fixedName(s.getFixedName() != null ? s.getFixedName() : s.getName())
                    .build());
        }
        return ServiceListResponseDTO.builder()
                .runningDeployment(entity.getRunningDeployment())
                .total(entity.getTotal())
                .count(entity.getCount())
                .services(items)
                .build();
    }

    /**
     * `GET /v1/software/relationService/{id}` 的装配：与列表端点同一套 `ServiceItemDTO`，
     * 但 `mappingInfo` **真的填**（三键是**数组**；列表端点那边是三键全 null 的空对象，两者不同）。
     * <p>`ingressInfo` 恒空数组（厂商读的 `proxy` 字段全仓库无赋值 ⇒ 恒 null）。
     */
    public static List<ServiceItemDTO> toRelationItems(List<ServiceRuntimeEntity> services) {
        List<ServiceItemDTO> items = new ArrayList<>();
        for (ServiceRuntimeEntity s : services) {
            items.add(ServiceItemDTO.builder()
                    .service(s.getServiceSnapshot())
                    .deployment(s.getDeploymentSnapshot())
                    .serviceAuth(toAuthDTO(s.getServiceAuth()))
                    .mappingInfo(toMappingInfo(s))
                    .hostIP(s.getHostIP())
                    .imageInfo(s.getImage())
                    .restartCount(s.getRestartCount())
                    .fixedName(s.getFixedName() != null ? s.getFixedName() : s.getName())
                    .build());
        }
        return items;
    }

    private static MappingInfoDTO toMappingInfo(ServiceRuntimeEntity s) {
        var mapping = s.getMappingInfo();
        if (mapping == null) {
            return MappingInfoDTO.builder().build();
        }
        return MappingInfoDTO.builder()
                .intranetInfo(mapping.getIntranetInfo())
                .nodePortInfo(mapping.getNodePortInfo())
                .ingressInfo(mapping.getIngressInfo())
                .build();
    }

    /**
     * `GET /v1/service/{serviceName}`（服务详情）的装配：厂商 `ServicesItem` 的
     * **20 键**（比列表项多 `terminated`/`hostIP`/`nodePortService`，且三个 `*Order` 是 Double）。
     *
     * <p>厂商详情端点里这几处是**字段初始化值**，从不赋值，我们照抄：
     * `volumeTypeList`/`softwareNameList`/`podInfoList`/`belongNodeList` = null，
     * `mappingInfo` = 空映射对象（`three-null` 形状，与列表端点一致），
     * `restartCount` = **0**（厂商详情里恒为 0，不是真实重启次数），
     * `cpuOrder`/`memoryOrder`/`gpuOrder` = **0.0**。
     */
    public static com.csm.paas.core.api.dto.ServiceDetailDTO toDetailResponse(ServiceRuntimeEntity s) {
        return com.csm.paas.core.api.dto.ServiceDetailDTO.builder()
                .service(s.getServiceSnapshot())
                .deployment(s.getDeploymentSnapshot())
                .serviceAuth(toAuthDTO(s.getServiceAuth()))
                .terminated(s.getTerminatedSnapshot())
                .hostIP(s.getHostIP())
                .nodePortService(toNodeService(s.getNodePortServiceSnapshot()))
                .imageInfo(s.getImage())
                .mappingInfo(MappingInfoDTO.builder().build())
                .restartCount(0)
                .cpuOrder(0.0)
                .memoryOrder(0.0)
                .gpuOrder(0.0)
                .nameUs(s.getNameUs())
                .nameFr(s.getNameFr())
                .build();
    }

    /**
     * 把 `<name>-nodeport` 的 K8s Service 快照**裁剪**成厂商 `NodeService`：`{ports:[…], type}`。
     * <p>读不到（快照为 null）⇒ `nodePortService` 为 **null**；读到了但没端口 ⇒ `ports` 是**空数组**。
     */
    @SuppressWarnings("unchecked")
    private static com.csm.paas.core.api.dto.NodeServiceDTO toNodeService(java.util.Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        Object spec = snapshot.get("spec");
        if (!(spec instanceof java.util.Map<?, ?> specMap)) {
            return com.csm.paas.core.api.dto.NodeServiceDTO.builder()
                    .ports(new ArrayList<>()).build();
        }
        java.util.List<com.csm.paas.core.api.dto.NodeServiceDTO.NodeServicePortDTO> ports = new ArrayList<>();
        Object rawPorts = ((java.util.Map<String, Object>) specMap).get("ports");
        if (rawPorts instanceof java.util.List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof java.util.Map<?, ?> port)) {
                    continue;
                }
                java.util.Map<String, Object> p = (java.util.Map<String, Object>) port;
                ports.add(com.csm.paas.core.api.dto.NodeServiceDTO.NodeServicePortDTO.builder()
                        .name(stringOrNull(p.get("name")))
                        .protocol(stringOrNull(p.get("protocol")))
                        .port(intOrNull(p.get("port")))
                        .targetPort(targetPortInt(p.get("targetPort")))
                        .appProtocol(stringOrNull(p.get("appProtocol")))
                        .nodePort(intOrNull(p.get("nodePort")))
                        .build());
            }
        }
        return com.csm.paas.core.api.dto.NodeServiceDTO.builder()
                .ports(ports)
                .type(stringOrNull(((java.util.Map<String, Object>) specMap).get("type")))
                .build();
    }

    private static String stringOrNull(Object value) {
        return value instanceof String str ? str : null;
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 厂商 `NodeServicePort.setTargetPort(targetPort.getIntValue())`：`IntOrString` 是**数字**时得到该数字，
     * 是**具名字符串**时 `getIntValue()` 返回 **null**（不抛异常）；这里对 Map 快照做同样的折算。
     */
    private static Integer targetPortInt(Object targetPort) {
        if (targetPort instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static ServiceAuthDTO toAuthDTO(ServiceAuthEntity auth) {
        if (auth == null) {
            return null;
        }
        return ServiceAuthDTO.builder()
                .serviceId(auth.getServiceId())
                .auth(auth.getAuth())
                .authType(auth.getAuthType())
                .authTime(auth.getAuthTime())
                .authInterval(auth.getAuthInterval())
                .build();
    }
}
