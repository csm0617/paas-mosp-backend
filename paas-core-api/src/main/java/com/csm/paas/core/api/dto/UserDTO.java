package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/user 的 data 形状（**14 个键**，比登录响应少 token/role/roles/subRoles/settingList/style）。 */
@Data
@Builder
public class UserDTO {
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
}
