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
    /**
     * 生日。`g_user.birth` 是 **`date` 列**，所以领域里用 `LocalDate`；
     * 与 JSON 的换算（含那条 8 小时时区坑）统一走 `DateTimes`，见其文档。
     */
    private java.time.LocalDate birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String lastLoginRole;
    private String nickname;
    /** 厂商 `User.uuid` 是 Integer（`g_user.uuid` 是 int 列，用于"用户数据额度"）。 */
    private Integer uuid;
    private String desc;
    private String avatar;
}
