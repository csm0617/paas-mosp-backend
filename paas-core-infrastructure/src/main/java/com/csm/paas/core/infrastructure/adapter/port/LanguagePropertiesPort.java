package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.language.adapter.port.ILanguagePropertiesPort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 读内置语言包：厂商从 `classpath:messages.properties`（zh_CN）/`classpath:messages_<lang>.properties` 读；
 * 我们的资源放在 `classpath:i18n/` 下（`messages.properties` / `messages_en_US.properties` /
 * `messages_fr_FR.properties`），所以路径前缀是 `i18n/`——**这是资源布局差异，不是行为差异**。
 */
@Component
public class LanguagePropertiesPort implements ILanguagePropertiesPort {

    private static final String I18N_DIR = "i18n/";
    private static final String ZH_CN = "zh_CN";

    @Override
    public Map<String, String> readBuiltinMessages(String lang) {
        String fileName = I18N_DIR + (ZH_CN.equals(lang) ? "messages.properties" : "messages_" + lang + ".properties");
        Properties properties = new Properties();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(fileName).getInputStream(), StandardCharsets.UTF_8))) {
            properties.load(reader);
        } catch (IOException e) {
            // 厂商：读失败 ⇒ 400 `globalization.export.from.resource`（带 lang）
            throw new WrongArgumentException("globalization.export.from.resource", lang);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }
}
