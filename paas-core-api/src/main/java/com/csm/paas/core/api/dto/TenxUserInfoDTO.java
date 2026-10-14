package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `com.glriverside.gaop.be.controller.response.TenxUserInfo`（`TenxUserInfo.java`）：
 * `GET /v1/user/tenx_user` 的列表元素，只有 `userName` / `apiToken` 两个键。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenxUserInfoDTO {

    private String userName;
    private String apiToken;
}
