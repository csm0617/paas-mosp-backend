package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 厂商 `UserRoles`（**16 键**，顺序照抄）：`GET /v1/user/list` 的条目。
 * <p>与 `UserInfoItemDTO`（11 键）的差异见 `findings/98`：去掉 `belongUsers`，多
 * `email/birth/phone/avatar/uuid` 与 `admin`；`nickname` 厂商**不设**（恒 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRolesItemDTO {

    private String id;
    private String username;
    private String nickname;
    private Integer status;
    private String email;
    private LocalDate birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private String avatar;
    private String lastLoginRole;
    private Integer uuid;
    private List<RoleItemDTO> roles;
    private List<SubRoleItemDTO> subRoles;
    private boolean admin;
}
