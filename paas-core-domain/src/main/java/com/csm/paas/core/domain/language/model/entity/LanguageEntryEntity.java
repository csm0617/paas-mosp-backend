package com.csm.paas.core.domain.language.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `LanguageEntity{id, description, textKey, module, content, abbr}`（`/v1/lang/detail` 的列表元素）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageEntryEntity {

    private Long id;
    private String description;
    private String textKey;
    private String module;
    private String content;
    private String abbr;
}
