package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 `User` 的 data 形状（**14 个键**，用于 `GET /v1/user/{userId}` 与 `GET /v1/user/{roleId}/users` 的条目）。 */
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
    /** 厂商 `User.uuid` 是 **Integer**（`g_user.uuid` 是 int 列）。 */
    private Integer uuid;
    private String desc;
    private String avatar;
}
