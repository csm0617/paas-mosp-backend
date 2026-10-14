package com.csm.paas.core.domain.language.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语言包版本（厂商 `LanguageVersion`）。
 * <p>`langVersion` 的 JSON 键就是 `langVersion`（厂商 PO 没有 `@JsonProperty`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageVersionEntity {

    private Long id;
    private String lang;
    private String langVersion;
    private Integer used;
    private Long createTime;
}
