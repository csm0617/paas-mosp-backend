package com.csm.paas.core.domain.language.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `POST /v1/lang/import` 的解析结果（POI 在 trigger 解析，领域层只落库）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageImportParseResultVO {

    /**
     * 生效语言：默认 `zh_CN`；第 1 行（Excel 第 2 行）第 5 列非空白时取该值并
     * `replaceAll("-","_")`（厂商 `BaseLanguageEnum.lang2Underline`）；取值异常则保持默认。
     */
    private String lang;

    /**
     * 词条行（Excel 第 2 行起）。**空列表等价于厂商的 `lastRowNum < 1`** ⇒ 导入直接返回 false。
     */
    private List<LanguageImportEntryVO> rows;
}
