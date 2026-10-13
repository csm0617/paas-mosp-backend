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
    /**
     * 生日。**类型照抄厂商的 `java.util.Date`**（`g_user.birth` 是 `date` 列）。
     * <p>契约（2026-10-14 探针实测）：请求必须是 **epoch 毫秒数字**（发 `"2000-01-02"` 这种 date-only 串
     * 厂商会 500/-1）；响应是 **ISO-8601 UTC 串**（如 `"2000-01-01T16:00:00.000Z"`）。
     * 用 `java.util.Date` + Boot 默认的 `WRITE_DATES_AS_TIMESTAMPS=false` 即可两边对齐。
     */
    private java.util.Date birth;
    private String phone;
    private Long updatedAt;
    private Long createdAt;
    private String lastLoginRole;
    private String nickname;
    /** 厂商 `User.uuid` 是 **Integer**（`g_user.uuid` 是 int 列）；这里曾误写为 String，见 docs/findings/22 §1.8。 */
    private Integer uuid;
    private String desc;
    private String avatar;
    private String token;
    private RoleItemDTO role;
    private List<Object> roles;
    private List<Object> subRoles;
    private List<Object> settingList;
    private String style;
}
