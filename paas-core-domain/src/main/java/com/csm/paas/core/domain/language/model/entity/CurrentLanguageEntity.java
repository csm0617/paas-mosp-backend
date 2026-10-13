package com.csm.paas.core.domain.language.model.entity;

import lombok.Builder;
import lombok.Data;

/** 当前语言与语言包版本（厂商 GET /v1/lang/current）。 */
@Data
@Builder
public class CurrentLanguageEntity {
    private String language;
    private String version;
}
