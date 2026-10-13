package com.csm.paas.core.domain.language.service;

import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;

/** 当前语言查询（厂商 GET /v1/lang/current）。 */
public interface ILanguageQueryService {

    CurrentLanguageEntity queryCurrent();
}
