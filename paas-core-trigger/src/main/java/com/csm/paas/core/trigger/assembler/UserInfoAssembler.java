package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.UserInfoItemDTO;
import com.csm.paas.core.api.dto.UserInfoListResponseDTO;
import com.csm.paas.core.domain.user.model.entity.UserInfoEntity;
import com.csm.paas.core.domain.user.model.entity.UserInfoPageEntity;

import java.util.List;

/**
 * 领域读模型 → 厂商响应 DTO（{@code GET /v1/user/info}）。
 * <p>契约依据：实测样本 {@code docs/findings/runtime-samples/v1_user_info.json}。
 */
public final class UserInfoAssembler {

    private UserInfoAssembler() {
    }

    public static UserInfoListResponseDTO toResponse(UserInfoPageEntity entity) {
        List<UserInfoItemDTO> list = entity.getList().stream()
                .map(UserInfoAssembler::toItem)
                .toList();
        return UserInfoListResponseDTO.builder().list(list).total(entity.getTotal()).build();
    }

    private static UserInfoItemDTO toItem(UserInfoEntity e) {
        return UserInfoItemDTO.builder()
                .id(e.getId())
                .nickname(e.getNickname())
                .username(e.getUsername())
                .status(e.getStatus())
                .updatedAt(e.getUpdatedAt())
                .createdAt(e.getCreatedAt())
                .desc(e.getDesc())
                .lastLoginRole(e.getLastLoginRole())
                .roles(e.getRoles())
                .subRoles(e.getSubRoles())
                .belongUsers(e.getBelongUsers())
                .build();
    }
}
