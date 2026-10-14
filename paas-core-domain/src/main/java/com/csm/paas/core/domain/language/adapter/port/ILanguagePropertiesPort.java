package com.csm.paas.core.domain.language.adapter.port;

import java.util.Map;

/**
 * 读**内置**语言包（厂商 `LanguageServiceImpl.getLangListFromProperties:296-343`：
 * `classpath:messages.properties` / `messages_<lang>.properties`）。
 *
 * <p>放在端口后面，是为了让 domain 不直接碰 classpath/IO。
 */
public interface ILanguagePropertiesPort {

    /**
     * 读某语言的内置词条（键顺序不可依赖，用 Map）。
     * <p>文件读不到 ⇒ 抛 `WrongArgumentException("globalization.export.from.resource", lang)`（由实现方抛）。
     */
    Map<String, String> readBuiltinMessages(String lang);
}
