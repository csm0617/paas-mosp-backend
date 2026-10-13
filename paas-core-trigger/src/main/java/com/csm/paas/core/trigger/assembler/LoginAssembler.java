package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.LoginUserDTO;
import com.csm.paas.core.api.dto.RoleSoftwareDTO;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.model.entity.UserEntity;

import java.util.List;

/** 登录结果 → 厂商契约 DTO（形状依据 docs/findings/runtime-samples/v1_pub_login.json）。 */
public final class LoginAssembler {

    private LoginAssembler() {
    }

    public static LoginUserDTO toResponse(LoginResultEntity result) {
        UserEntity u = result.getUser();
        return LoginUserDTO.builder()
                .id(u.getId()).username(u.getUsername()).password(null).status(u.getStatus())
                .email(u.getEmail()).birth(u.getBirth()).phone(u.getPhone())
                .updatedAt(u.getUpdatedAt()).createdAt(u.getCreatedAt())
                .lastLoginRole(u.getLastLoginRole()).nickname(u.getNickname()).uuid(u.getUuid())
                .desc(u.getDesc()).avatar(u.getAvatar())
                .token(result.getToken())
                .role(RoleListAssembler.toItemDTO(result.getRole()))
                .roles(null).subRoles(null).settingList(null)
                .style("default")
                .build();
    }

    private static List<RoleSoftwareDTO> toSoftwareDTOs(List<RoleSoftwareEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(s -> RoleSoftwareDTO.builder()
                .id(s.getId()).roleId(s.getRoleId()).appId(s.getAppId()).appName(s.getAppName())
                .url(s.getUrl()).updatedAt(s.getUpdatedAt()).createdAt(s.getCreatedAt())
                .build()).toList();
    }
}
