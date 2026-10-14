package com.csm.paas.core.domain.language.service;

import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;

/** 当前语言查询（厂商 GET /v1/lang/current）。 */
public interface ILanguageQueryService {

    CurrentLanguageEntity queryCurrent();

    /**
     * 语言包版本列表（分页）——厂商 `GET /v1/lang/version` → `LanguageServiceImpl.getLangVersionList:267-281`，
     * 规格见 `docs/findings/137`。
     *
     * <p>照抄厂商的关键字处理：**先把空格全部去掉**（`replaceAll(" +","")`）再把 `-` 换成 `_`
     * （`BaseLanguageEnum.lang2Underline`）；结果为空则**不过滤**。排序恒为 `create_time DESC`，
     * `total` 是**同一 where 的 COUNT**。`page`/`size` 由调用方用 `PageUtils` 归一化
     * （厂商 `SqlUtils.normalizePage/normalizeSize` 语义一致：page<0⇒1、size null⇒20、size<=0⇒100）。
     */
    com.csm.paas.core.domain.language.model.entity.LanguageVersionPageEntity queryLanguageVersionList(
            int page, int size, String keyword);
}
