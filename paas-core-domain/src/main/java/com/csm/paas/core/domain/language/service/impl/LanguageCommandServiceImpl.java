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

    private static final java.util.Set<String> BASE_LANGUAGES = java.util.Set.of("zh_CN", "en_US", "fr_FR");

    private final ILanguageVersionRepository languageVersionRepository;
    private final com.csm.paas.core.domain.language.adapter.port.ILanguageI18nDirectoryPort i18nDirectoryPort;

    @Override
    public boolean setLanguageVersion(String lang, String version) {
        if (lang == null || version == null) {
            return false;
        }
        String finalLang = lang.replaceAll("-", "_");
        if (DEFAULT_VERSION.equals(version)) {
            updateLanguageVersion(DEFAULT_VERSION, finalLang);
            return setDefaultVersionLang(finalLang);
        }
        if (languageVersionRepository.countByLangAndVersion(finalLang, version) == 0) {
            return false;
        }
        return updateLanguageVersion(version, finalLang) > 0;
    }

    /**
     * 厂商 `updateLanguageVersion:464-467`：**先把全局 `used=1` 清 0**，再把 (lang, version) 置 1，
     * 返回**第二步**的受影响行数。
     */
    private int updateLanguageVersion(String version, String lang) {
        languageVersionRepository.resetUsed();
        return languageVersionRepository.setUsed(lang, version);
    }

    /**
     * 厂商 `setDefaultVersionLang:469-483`：非基础语言 ⇒ false；`./i18n` 不存在 ⇒ true；
     * 存在 ⇒ 删掉整个目录后 true。
     */
    private boolean setDefaultVersionLang(String lang) {
        if (!BASE_LANGUAGES.contains(lang)) {
            return false;
        }
        if (!i18nDirectoryPort.exists()) {
            log.info("国际化目录{}不存在，无需删除", "./i18n");
            return true;
        }
        i18nDirectoryPort.delete();
        return true;
    }

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
