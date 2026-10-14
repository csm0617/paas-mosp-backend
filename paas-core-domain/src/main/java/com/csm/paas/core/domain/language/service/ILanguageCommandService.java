package com.csm.paas.core.domain.language.service;

/**
 * 语言包写操作（厂商 `LanguageController` 的 `POST /version`、`DELETE /remove`、`POST /import`）。
 */
public interface ILanguageCommandService {

    /**
     * 删除某个语言包版本（厂商 `DELETE /v1/lang/remove?lang=&version=` →
     * `LanguageServiceImpl.deleteLangVersion:349-362`，规格见 `docs/findings/138`）。
     *
     * <p>规则：
     * <ol>
     *   <li>`lang` 先做 `-` → `_` 归一化（`BaseLanguageEnum.lang2Underline`）；</li>
     *   <li>**`version` 是 `default` ⇒ 直接返回 false**（不允许删默认版本）；</li>
     *   <li>该语言+版本**不存在 ⇒ 返回 true**（幂等）；</li>
     *   <li>否则：先删 `g_language` 里该语言+版本的**全部词条**，再按主键删 `g_language_version` 那一行，
     *       **返回"版本行是否真的删掉了"**（`delete > 0`）。</li>
     * </ol>
     */
    boolean deleteLanguageVersion(String lang, String version);
}
