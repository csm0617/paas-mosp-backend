package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/lang/current 的 data 形状：{language, version}。 */
@Data
@Builder
public class CurrentLanguageDTO {
    private String language;
    private String version;
}
