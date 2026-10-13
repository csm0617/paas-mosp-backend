package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.UserDTO;
import com.csm.paas.core.domain.user.model.entity.UserEntity;

/** 用户实体 → 厂商 GET /v1/user 的 DTO。口令置 null（厂商行为）。 */
public final class UserAssembler {

    private UserAssembler() {
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
