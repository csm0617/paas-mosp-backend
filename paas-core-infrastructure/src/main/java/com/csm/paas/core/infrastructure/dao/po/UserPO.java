package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class UserPO {
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
    /** 对应 `g_user.uuid`（int 列）；厂商模型里是 Integer。 */
    private Integer uuid;
    private String desc;
    private String avatar;
}
