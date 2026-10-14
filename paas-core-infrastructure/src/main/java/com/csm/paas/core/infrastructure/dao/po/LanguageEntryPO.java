package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_language` 的投影（厂商 `Language`：id/textKey/content/module/abbr/description）。
 * <p>注意真实库比厂商 migration 的 DDL 多 `module`（NOT NULL）与 `abbr`/`description`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageEntryPO {

    private Long id;
    private String textKey;
    private String content;
    private String lang;
    private String langVersion;
    private String module;
    private String abbr;
    private String description;
}
