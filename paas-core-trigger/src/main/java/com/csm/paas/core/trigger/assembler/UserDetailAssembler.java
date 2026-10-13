package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.RoleItemDTO;
import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.api.dto.UserDetailDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.model.entity.UserDetailEntity;
import com.csm.paas.core.types.util.DateTimes;

import java.util.List;

/**
 * 领域读模型 → 厂商响应 DTO（{@code GET /v1/user/detailByUserId}）。
 * <p>契约依据：实测样本 {@code docs/findings/runtime-samples/v1_user_detailByUserId.json}。
 */
public final class UserDetailAssembler {

    private UserDetailAssembler() {
    }

    public static UserDetailDTO toResponse(UserDetailEntity e) {
        return UserDetailDTO.builder()
                .id(e.getId())
                .username(e.getUsername())
                .nickname(e.getNickname())
                .status(e.getStatus())
                .email(e.getEmail())
                .birth(DateTimes.toDate(e.getBirth()))
                .phone(e.getPhone())
                .updatedAt(e.getUpdatedAt())
                .createdAt(e.getCreatedAt())
                .desc(e.getDesc())
                .avatar(e.getAvatar())
                .lastLoginRole(e.getLastLoginRole())
                .uuid(e.getUuid())
                .roles(toRoleDTOs(e.getRoles()))
                .subRoles(toSubRoleDTOs(e.getSubRoles()))
                .admin(e.getAdmin())
                .belongRole(toRoleDTOs(e.getBelongRole()))
                .build();
    }

    private static List<RoleItemDTO> toRoleDTOs(List<RoleEntity> roles) {
        return roles == null ? null : roles.stream().map(RoleListAssembler::toItemDTO).toList();
    }

    private static List<SubRoleItemDTO> toSubRoleDTOs(List<SubRoleEntity> subRoles) {
        return subRoles == null ? null : subRoles.stream().map(SubRoleListAssembler::toItemDTO).toList();
    }
}
