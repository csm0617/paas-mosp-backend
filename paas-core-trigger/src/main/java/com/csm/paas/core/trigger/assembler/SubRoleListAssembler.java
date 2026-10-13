package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.api.dto.SubRoleListResponseDTO;
import com.csm.paas.core.api.dto.SubRoleRoleDTO;
import com.csm.paas.core.api.dto.SubRoleUserDTO;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;

import java.util.List;

/**
 * 领域读模型 → 厂商响应 DTO（{@code GET /v1/sub_role/switchRole}）。
 * <p>契约依据：实测样本 {@code docs/findings/runtime-samples/v1_sub_role_switchRole_nonempty.json}
 * （以及本环境的空列表样本 {@code v1_sub_role_switchRole.json}）。
 */
public final class SubRoleListAssembler {

    private SubRoleListAssembler() {
    }

    public static SubRoleListResponseDTO toResponse(SubRoleListEntity entity) {
        List<SubRoleItemDTO> list = entity.getList().stream().map(SubRoleListAssembler::toItem).toList();
        return SubRoleListResponseDTO.builder().list(list).total(entity.getTotal()).build();
    }

    private static SubRoleItemDTO toItem(SubRoleEntity e) {
        return SubRoleItemDTO.builder()
                .id(e.getId())
                .key(e.getKey())
                .name(e.getName())
                .nameFr(e.getNameFr())
                .nameUs(e.getNameUs())
                .updatedAt(e.getUpdatedAt())
                .createdAt(e.getCreatedAt())
                .desc(e.getDesc())
                .userList(e.getUserList() == null ? null : e.getUserList().stream()
                        .map(u -> SubRoleUserDTO.builder().userId(u.getUserId()).userName(u.getUserName()).build())
                        .toList())
                .roleList(e.getRoleList() == null ? null : e.getRoleList().stream()
                        .map(r -> SubRoleRoleDTO.builder()
                                .roleId(r.getRoleId()).roleKey(r.getRoleKey()).roleName(r.getRoleName())
                                .build())
                        .toList())
                .build();
    }
}
