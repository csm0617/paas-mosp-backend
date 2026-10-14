package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.SubRoleItemDTO;
import com.csm.paas.core.api.dto.UserRoleListResponseDTO;
import com.csm.paas.core.api.dto.UserRolesItemDTO;
import com.csm.paas.core.domain.user.model.entity.UserRolesEntity;
import com.csm.paas.core.domain.user.model.entity.UserRolesPageEntity;

import java.util.List;

/**
 * `GET /v1/user/list` 的装配。
 *
 * <p>`roles` **复用 `/v1/role` 的 `RoleListAssembler.toItemDTO`**（厂商两边是同一个 `Role` 形状，
 * 见 `findings/98` 的实测样本）；`nickname` **刻意不填**（厂商 `getUserRoles` 没有 setNickname）。
 */
public final class UserRolesAssembler {

    private UserRolesAssembler() {
    }

    public static UserRoleListResponseDTO toResponse(UserRolesPageEntity entity) {
        return UserRoleListResponseDTO.builder()
                .list(entity.getList().stream().map(UserRolesAssembler::toItem).toList())
                .total(entity.getTotal())
                .build();
    }

    private static UserRolesItemDTO toItem(UserRolesEntity e) {
        return UserRolesItemDTO.builder()
                .id(e.getId())
                .username(e.getUsername())
                // nickname **不设**：厂商 getUserRoles 没有 setNickname ⇒ 恒 null
                .status(e.getStatus())
                .email(e.getEmail())
                .birth(e.getBirth())
                .phone(e.getPhone())
                .updatedAt(e.getUpdatedAt())
                .createdAt(e.getCreatedAt())
                .desc(e.getDesc())
                .avatar(e.getAvatar())
                .lastLoginRole(e.getLastLoginRole())
                .uuid(e.getUuid())
                .roles(e.getRoles() == null ? List.of()
                        : e.getRoles().stream().map(RoleListAssembler::toItemDTO).toList())
                // subRoles：映射 `SubRoleEntity` 的全部标量字段（本环境该列表为空，形状未经对拉验证；
                // 非空时的 `userList`/`roleList` 仍未映射——见 findings/99 的如实标注）
                .subRoles(e.getSubRoles() == null ? List.of()
                        : e.getSubRoles().stream()
                        .map(s -> SubRoleItemDTO.builder()
                                .id(s.getId()).key(s.getKey()).name(s.getName())
                                .nameFr(s.getNameFr()).nameUs(s.getNameUs())
                                .updatedAt(s.getUpdatedAt()).createdAt(s.getCreatedAt()).desc(s.getDesc())
                                .build())
                        .toList())
                .admin(e.isAdmin())
                .build();
    }
}
