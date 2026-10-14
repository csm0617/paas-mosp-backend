package com.csm.paas.core.domain.language.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `LanguageVersionInfo{versionList, total}`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageVersionPageEntity {

    private List<LanguageVersionEntity> versionList;
    private long total;
}
