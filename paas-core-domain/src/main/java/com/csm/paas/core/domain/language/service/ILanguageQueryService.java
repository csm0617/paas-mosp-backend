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

    /**
     * 语言包详情（厂商 `GET /v1/lang/detail` → `LanguageServiceImpl.getLangList:284-294`，规格见 `docs/findings/139`）。
     *
     * <p>规则：
     * <ol>
     *   <li>`lang` 先 `-` → `_` 归一化；</li>
     *   <li>`version` 为空白 ⇒ 用**当前版本**（`used=1` 那一行的 `lang_version`，没有则 `default`）；</li>
     *   <li>`version == "default"` ⇒ 读**内置**语言包（`classpath:i18n/messages*.properties`），
     *       每条映射成 `{textKey, content, module="gaop", langVersion="default"}`（`id`/`description`/`abbr` 为 null），
     *       `module` 字段固定回填 `"gaop"`；</li>
     *   <li>否则查 `g_language`（`module` 非空白时再按 module 过滤）；</li>
     *   <li>`total` 就是**列表长度**（不是 COUNT 查询）。</li>
     * </ol>
     */
    com.csm.paas.core.domain.language.model.entity.LanguageInfoEntity queryLanguageDetail(
            String lang, String version, String module);
}
