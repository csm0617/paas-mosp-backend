package com.csm.paas.core.domain.language.adapter.repository;

import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;

import java.util.Optional;

/** 语言包版本仓储（g_language_version，`used=1` 为当前）。 */
public interface ILanguageVersionRepository {

    Optional<CurrentLanguageEntity> queryCurrent();
}
