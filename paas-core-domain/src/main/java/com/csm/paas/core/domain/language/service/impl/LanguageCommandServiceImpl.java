package com.csm.paas.core.domain.language.service.impl;

import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.service.ILanguageCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 语言包写操作实现（规格见 `docs/findings/138`）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageCommandServiceImpl implements ILanguageCommandService {

    private static final String DEFAULT_VERSION = "default";

    private final ILanguageVersionRepository languageVersionRepository;

    @Override
    public boolean deleteLanguageVersion(String lang, String version) {
        String finalLang = lang == null ? null : lang.replaceAll("-", "_");
        // 厂商：不允许删默认版本
        if (version != null && !version.isEmpty() && DEFAULT_VERSION.equals(version)) {
            return false;
        }
        var row = languageVersionRepository.queryByLangAndVersion(finalLang, version);
        if (row.isEmpty()) {
            return true;   // 不存在也算成功（幂等）
        }
        var languageVersion = row.get();
        languageVersionRepository.deleteLanguageEntries(languageVersion.getLang(), languageVersion.getLangVersion());
        return languageVersionRepository.deleteVersionById(languageVersion.getId()) > 0;
    }
}
