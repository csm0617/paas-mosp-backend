package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_language_version` 的投影（厂商 `LanguageVersion`：id/lang/langVersion/used/createTime）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageVersionPO {

    private Long id;
    private String lang;
    private String langVersion;
    private Integer used;
    private Long createTime;
}
