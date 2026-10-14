package com.csm.paas.core.domain.language.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/lang/import` 的一行词条（厂商 `LanguageServiceImpl.importLanguageExcel:114-246`
 * 里逐行构造的 `Language`）。
 *
 * <p>`lang` / `langVersion` 由领域服务在算出 `versionInfo` 后回填（与厂商先建 `Language`
 * 再 `setLang/setLangVersion` 等价）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageImportEntryVO {

    /** Excel 第 0 列；空 ⇒ 400 `language.import.module.must.not.null`（带行号）。 */
    private String module;

    /** Excel 第 1 列；空 ⇒ 400 `language.import.key.must.not.null`。 */
    private String textKey;

    /** Excel 第 2 列（可空）。 */
    private String description;

    /** Excel 第 3 列；空 ⇒ 400 `language.import.content.must.not.null`。 */
    private String content;

    /** Excel 第 4 列（可空）。 */
    private String abbr;

    private String lang;
    private String langVersion;
}
