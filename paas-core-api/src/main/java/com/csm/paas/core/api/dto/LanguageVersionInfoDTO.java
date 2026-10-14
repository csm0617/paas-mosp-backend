package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `LanguageVersionInfo{versionList, total}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LanguageVersionInfoDTO {

    private List<LanguageVersionDTO> versionList;
    private long total;

    /** 厂商 `LanguageVersion{id, lang, langVersion, used, createTime}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LanguageVersionDTO {
        private Long id;
        private String lang;
        private String langVersion;
        private Integer used;
        private Long createTime;
    }
}
