package com.csm.paas.core.domain.licence.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 授权信息（厂商 GET /v1/licence/info 的 data 形状）。
 * <p>字段语义照厂商 `NewLicenceInfo`：未激活时 `expired=true`、`authStatus=false`、`end=-1`、`endIso8601=null`，
 * 只给 `trialPeriod` 与 `currentTime`。
 */
@Data
@Builder
public class LicenceInfoEntity {
    private Long end;
    private String endIso8601;
    private String code;
    private Boolean expired;
    private Boolean authStatus;
    private Long currentTime;
    private Long trialPeriod;
}
