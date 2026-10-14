package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `LanguageInfo{languageList, total, module, lang, langVersion}`（`GET /v1/lang/detail` 的响应）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageInfoDTO {

    private List<LanguageEntryDTO> languageList;
    private long total;
    private String module;
    private String lang;
    private String langVersion;

    /** 厂商 `LanguageEntity{id, description, textKey, module, content, abbr}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LanguageEntryDTO {
        private Long id;
        private String description;
        private String textKey;
        private String module;
        private String content;
        private String abbr;
    }
}
