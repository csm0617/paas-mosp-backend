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
