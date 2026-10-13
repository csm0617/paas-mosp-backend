package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/licence/info 的 data 形状（7 键）。 */
@Data
@Builder
public class LicenceInfoDTO {
    private Long end;
    private String endIso8601;
    private String code;
    private Boolean expired;
    private Boolean authStatus;
    private Long currentTime;
    private Long trialPeriod;
}
