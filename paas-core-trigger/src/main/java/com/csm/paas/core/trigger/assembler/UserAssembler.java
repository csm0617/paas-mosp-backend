package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.UserDTO;
import com.csm.paas.core.api.dto.UserListByRoleDTO;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.entity.UserListByRoleEntity;

/** 用户实体 → 厂商 GET /v1/user 的 DTO。口令置 null（厂商行为）。 */
public final class UserAssembler {

    private UserAssembler() {
    }

    /**
     * 战位下用户列表（`GET /v1/user/{roleId}/users`）。
     * <p><b>与厂商的一处刻意偏离</b>：厂商这个端点把 `password`（BCrypt 摘要）**原样返回**给前端；
     * 我们统一置 null（与 `/v1/user`、`POST /v1/user` 同一条 L3 账）。
     */
    public static UserListByRoleDTO toResponse(UserListByRoleEntity entity) {
        return UserListByRoleDTO.builder()
                .userList(entity.getUserList().stream().map(UserAssembler::toResponse).toList())
                .total(entity.getTotal())
                .build();
    }

    public static UserDTO toResponse(UserEntity u) {
        return UserDTO.builder()
                .id(u.getId()).username(u.getUsername()).password(null).status(u.getStatus())
                .email(u.getEmail()).birth(com.csm.paas.core.types.util.DateTimes.toDate(u.getBirth())).phone(u.getPhone())
                .updatedAt(u.getUpdatedAt()).createdAt(u.getCreatedAt())
                .lastLoginRole(u.getLastLoginRole()).nickname(u.getNickname()).uuid(u.getUuid())
                .desc(u.getDesc()).avatar(u.getAvatar())
                .build();
    }
}
