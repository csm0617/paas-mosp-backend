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

    @Override
    public boolean importLanguageExcel(String version,
            com.csm.paas.core.domain.language.model.valobj.LanguageImportParseResultVO parsed) {
        var rows = parsed == null ? null : parsed.getRows();
        // 厂商 :99-103：`lastRowNum < 1` ⇒ 记日志后返回 false（POI 侧等价于"没有词条行"）
        if (rows == null || rows.isEmpty()) {
            log.info("表格无内容，或内容不匹配,无法进行导入语言信息");
            return false;
        }
        long start = System.currentTimeMillis();
        String lang = parsed.getLang();
        String versionInfo;
        com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity langVersion =
                new com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity();
        // 厂商在 i==1 时先 setLang（词条非空 ⇒ 一定会走到）
        langVersion.setLang(lang);
        long currentTime = System.currentTimeMillis();
        if (version == null || version.isBlank()) {
            // 厂商 :130-146：查"当天"已有版本数 ⇒ yyyyMMddV(size+1)
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
            String formattedDate = today.format(formatter);
            long todayStartTimestamp = java.time.LocalDateTime.of(today, java.time.LocalTime.MIN)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long todayEndTimestamp = java.time.LocalDateTime.of(today, java.time.LocalTime.MAX)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long size = languageVersionRepository.countVersionsCreatedBetween(lang, todayStartTimestamp, todayEndTimestamp);
            versionInfo = formattedDate + "V" + (size + 1);
            langVersion.setLangVersion(versionInfo);
            langVersion.setCreateTime(currentTime);
            langVersion.setUsed(0);
        } else {
            versionInfo = version;
            // 厂商 :151-156：已存在 ⇒ 版本对象**只保留 lang**，其余保持 null（但仍 insertSelective）
            if (languageVersionRepository.queryByLangAndVersion(lang, version).isEmpty()) {
                langVersion.setLangVersion(version);
                langVersion.setCreateTime(currentTime);
                langVersion.setUsed(0);
            }
        }
        // 厂商 :243-245：逐行 setLang/setLangVersion
        for (var row : rows) {
            row.setLang(lang);
            row.setLangVersion(versionInfo);
        }
        // 厂商 :247 checkDuplicateKeyGroupByModule
        checkDuplicateKeyGroupByModule(rows);
        // 厂商 :248-253：每 4000 条一批
        int batchSize = 4000;
        for (int i = 0; i < rows.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, rows.size());
            languageVersionRepository.insertLanguageEntries(rows.subList(i, endIndex));
        }
        // 厂商 :254
        languageVersionRepository.insertLanguageVersionSelective(langVersion);
        log.info("总共花费时间:{}", System.currentTimeMillis() - start);
        return true;
    }

    /**
     * 厂商 `LanguageServiceImpl.checkDuplicateKeyGroupByModule:439-462`，逐行照抄：
     * 按 module 分组，组内找**重复 textKey**；把每个 module 的重复键拼成 `module:k1,k2`
     * （module 之间用**空格**分隔，末尾多出的分隔符被 `setLength(len-1)` 去掉）。
     */
    private static void checkDuplicateKeyGroupByModule(
            java.util.List<com.csm.paas.core.domain.language.model.valobj.LanguageImportEntryVO> rows) {
        java.util.Map<String, java.util.List<com.csm.paas.core.domain.language.model.valobj.LanguageImportEntryVO>>
                duplicateCountMap = rows.stream().collect(java.util.stream.Collectors.groupingBy(
                        com.csm.paas.core.domain.language.model.valobj.LanguageImportEntryVO::getModule));
        StringBuilder sb = new StringBuilder();
        for (var entry : duplicateCountMap.entrySet()) {
            java.util.HashSet<String> set = new java.util.HashSet<>();
            java.util.HashSet<String> duplicateSet = new java.util.HashSet<>();
            for (var row : entry.getValue()) {
                if (!set.add(row.getTextKey())) {
                    duplicateSet.add(row.getTextKey());
                }
            }
            if (duplicateSet.isEmpty()) {
                continue;
            }
            sb.append(entry.getKey()).append(':');
            for (String s : duplicateSet) {
                sb.append(s).append(',');
            }
            sb.setLength(sb.length() - 1);
            sb.append(' ');
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "language.import.duplicate.key", sb.toString());
        }
    }
}
