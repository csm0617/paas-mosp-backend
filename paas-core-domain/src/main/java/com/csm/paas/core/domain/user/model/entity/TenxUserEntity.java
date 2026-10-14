package com.csm.paas.core.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 天速云用户（厂商 `com.glriverside.gaop.be.controller.response.tenx.TenxUsers` 的
 * `userName` / `apiToken` 两个字段——本次只用到这两个）。
 */
@Data
@Builder
public class TenxUserEntity {

    private String userName;
    private String apiToken;
}
