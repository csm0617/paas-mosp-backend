package com.csm.paas.core.domain.language.adapter.port;

import java.util.Optional;

/**
 * i18n **自动名**读写（厂商 `I18nUtil.getAutoNameMessage` / `saveAutoNameMessage`，
 * `utils/I18nUtil.java:73-109`）。
 *
 * <p>它不是语言包，也不是 `g_language`：厂商把自动名存在 **`g_setting`** 里，
 * 键形如 `i18n.<module>.<language>.<key>`（例：`i18n.role.en_US.admin`）。
 * 读的时候**值为空白等同不存在**（厂商 `StringUtils.isBlank(setting.getVal())` ⇒ 返回 null）。
 */
public interface ILanguageAutoNamePort {

    /**
     * @param module   模块名（本仓库用到的是 `role`、`subrole`）
     * @param language `en_US` / `fr_FR`
     * @param key      业务主键（战位/子角色的 `key`）
     * @return 自动名；**键不存在或值为空白** ⇒ `Optional.empty()`（厂商返回 null）
     */
    Optional<String> queryAutoName(String module, String language, String key);
}
