package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 登录响应的 data（20 个键，逐字对齐运行时样本）。 */
@Data
@Builder
public class LoginUserDTO {
    private String id;
    private String username;
    private String password;
    private Integer status;
    private String email;
    private Long birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String lastLoginRole;
    private String nickname;
    private String uuid;
    private String desc;
    private String avatar;
    private String token;
    private RoleItemDTO role;
    private List<Object> roles;
    private List<Object> subRoles;
    private List<Object> settingList;
    private String style;
}
