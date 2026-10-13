package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.RoleListResponseDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;

import java.util.List;

/** 战位领域实体 → 厂商契约 DTO。抽出来是为了能离线回放测试。 */
public final class RoleListAssembler {

    private RoleListAssembler() {
    }

    public static RoleListResponseDTO toResponse(RoleListEntity entity) {
        return RoleListResponseDTO.builder()
                .list(entity.getList().stream().map(RoleListAssembler::toItemDTO).toList())
                .total(entity.getTotal())
                .build();
    }

    private static RoleItemDTO toItemDTO(RoleEntity role) {
        return RoleItemDTO.builder()
                .id(role.getId())
                .key(role.getKey())
                .name(role.getName())
                .nameFr(role.getNameFr())
                .nameUs(role.getNameUs())
                .updatedAt(role.getUpdatedAt())
                .createdAt(role.getCreatedAt())
                .desc(role.getDesc())
                .type(role.getType())
                .onLineUserList(null)
                .softwareList(toSoftwareDTOs(role.getSoftwareList()))
                .admin(role.getAdmin())
                .dev(role.getDev())
                .adminOrDev(role.getAdminOrDev())
                .build();
    }

    private static List<RoleSoftwareDTO> toSoftwareDTOs(List<RoleSoftwareEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(s -> RoleSoftwareDTO.builder()
                .id(s.getId())
                .roleId(s.getRoleId())
                .appId(s.getAppId())
                .appName(s.getAppName())
                .url(s.getUrl())
                .updatedAt(s.getUpdatedAt())
                .createdAt(s.getCreatedAt())
                .build()).toList();
    }
}
