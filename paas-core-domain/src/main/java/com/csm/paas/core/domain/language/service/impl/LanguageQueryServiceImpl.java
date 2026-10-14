package com.csm.paas.core.domain.language.service.impl;

import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;
import com.csm.paas.core.domain.language.service.ILanguageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 当前语言：取 `g_language_version` 里 `used=1` 的行；没有则回落厂商默认
 * (`zh_CN` / `default`，见 `LanguageServiceImpl.getCurrentLanguageWithVersion()`）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageQueryServiceImpl implements ILanguageQueryService {

    private static final String DEFAULT_LANGUAGE = "zh_CN";
    private static final String DEFAULT_VERSION = "default";

    private static final String PAAS_MODULE = "gaop";

    private final ILanguageVersionRepository languageVersionRepository;
    private final com.csm.paas.core.domain.language.adapter.port.ILanguagePropertiesPort languagePropertiesPort;

    @Override
    public CurrentLanguageEntity queryCurrent() {
        return languageVersionRepository.queryCurrent()
                .orElseGet(() -> CurrentLanguageEntity.builder()
                        .language(DEFAULT_LANGUAGE).version(DEFAULT_VERSION).build());
    }

    @Override
    public com.csm.paas.core.domain.language.model.entity.LanguageInfoEntity queryLanguageDetail(
            String lang, String version, String module) {
        String finalLang = lang == null ? null : lang.replaceAll("-", "_");
        String finalVersion = version;
        if (finalVersion == null || finalVersion.isBlank()) {
            finalVersion = queryCurrent().getVersion();
        }
        java.util.List<com.csm.paas.core.domain.language.model.entity.LanguageEntryEntity> entries;
        if (DEFAULT_VERSION.equals(finalVersion)) {
            entries = languagePropertiesPort.readBuiltinMessages(finalLang).entrySet().stream()
                    .map(e -> com.csm.paas.core.domain.language.model.entity.LanguageEntryEntity.builder()
                            .textKey(e.getKey()).content(e.getValue())
                            .module(PAAS_MODULE).build())
                    .toList();
            // 厂商：default 分支把 module 固定成 PAAS_MODULE 回填
            module = PAAS_MODULE;
        } else {
            entries = languageVersionRepository.queryLanguages(finalLang, finalVersion, module);
        }
        return com.csm.paas.core.domain.language.model.entity.LanguageInfoEntity.builder()
                .languageList(entries)
                .total(entries.size())
                .module(module)
                .lang(finalLang)
                .langVersion(finalVersion)
                .build();
    }

    @Override
    public com.csm.paas.core.domain.language.model.entity.LanguageVersionPageEntity queryLanguageVersionList(
            int page, int size, String keyword) {
        // 厂商：先去空格，再 lang2Underline（`-` → `_`）
        String normalized = null;
        if (keyword != null) {
            normalized = keyword.replaceAll(" +", "").replaceAll("-", "_");
        }
        String likeKeyword = normalized == null || normalized.isEmpty() ? null : "%" + normalized + "%";
        int offset = (page - 1) * size;
        return com.csm.paas.core.domain.language.model.entity.LanguageVersionPageEntity.builder()
                .versionList(languageVersionRepository.queryLanguageVersionPage(likeKeyword, offset, size))
                .total(languageVersionRepository.countLanguageVersions(likeKeyword))
                .build();
    }
}
