package com.csm.paas.core.domain.language.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `LanguageInfo{languageList, total, module, lang, langVersion}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageInfoEntity {

    private List<LanguageEntryEntity> languageList;
    private long total;
    private String module;
    private String lang;
    private String langVersion;
}
