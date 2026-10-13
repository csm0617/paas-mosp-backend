package com.csm.paas.core.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;

/** 用户（表 g_user）。 */
@Data
@Builder
public class UserEntity {
    private String id;
    private String username;
    /** BCrypt 摘要；响应里必须置 null（厂商行为）。 */
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
