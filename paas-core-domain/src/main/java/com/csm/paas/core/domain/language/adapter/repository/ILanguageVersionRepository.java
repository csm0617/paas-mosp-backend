package com.csm.paas.core.domain.language.adapter.repository;

import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;

import java.util.Optional;

/** 语言包版本仓储（g_language_version，`used=1` 为当前）。 */
public interface ILanguageVersionRepository {

    Optional<CurrentLanguageEntity> queryCurrent();

    /** 一页语言包版本（`likeKeyword` 为 null 表示不过滤；offset/size 由调用方归一化）。 */
    java.util.List<com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity> queryLanguageVersionPage(
            String likeKeyword, int offset, int size);

    /** 同一条 where 的总数。 */
    long countLanguageVersions(String likeKeyword);

    /** 某语言+某版本的版本行（取第 0 条）。 */
    java.util.Optional<com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity>
    queryByLangAndVersion(String lang, String version);

    /** 按主键删版本行，返回受影响行数。 */
    int deleteVersionById(Long id);

    /** 删某语言+某版本下的全部词条。 */
    void deleteLanguageEntries(String lang, String version);
}
