package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class UserPO {
    private String id;
    private String username;
    private String password;
    private Integer status;
    private String email;
    /** 对应 `g_user.birth`（**`date` 列**）——用 `LocalDate`，避免驱动把日期读成带时区的瞬时值。 */
    private java.time.LocalDate birth;
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
