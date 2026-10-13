package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 厂商 `GET /v1/user/detailByUserId` 的 data（`UserDetailInfo`，**17 个键**，顺序照抄实测样本）。
 * <p>`roles`/`belongRole` 是 **Role 对象**（16 键形状），`subRoles` 是 SubRole 对象——
 * **与 `/v1/user/info` 的同名字段形状完全不同**（那里 `roles` 是字符串数组）。见 `docs/findings/25`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDTO {

    private String id;
    private String username;
    private String nickname;
    private Integer status;
    private String email;
    private Date birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private String avatar;
    private String lastLoginRole;
    private Integer uuid;
    private List<RoleItemDTO> roles;
    private List<SubRoleItemDTO> subRoles;
    private Boolean admin;
    private List<RoleItemDTO> belongRole;
}
