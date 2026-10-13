package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/user/get data.list[] 的单元素形状：{username, lastLoginRole, avatar}。 */
@Data
@Builder
public class UserLoginItemDTO {
    private String username;
    private String lastLoginRole;
    private String avatar;
}
