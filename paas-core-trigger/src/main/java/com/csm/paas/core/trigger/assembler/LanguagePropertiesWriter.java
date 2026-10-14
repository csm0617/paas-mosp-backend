package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.language.model.entity.LanguageEntryEntity;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * 厂商 `LanguageServiceImpl.exportLanguage2Properties:393-411` 的等价实现：
 * 把当前语言包写成 `./i18n/messages_<lang>.properties`（`lang` 里的 `-` 换成 `_`）。
 *
 * <p>照抄点：路径是**相对工作目录**的 `./i18n`；目录不存在则创建；用 `Properties.store`（**无注释行**，
 * 厂商第二个参数传 null）；**词条顺序不可依赖**。
 */
public final class LanguagePropertiesWriter {

    private LanguagePropertiesWriter() {
    }

    public static Path write(String lang, List<LanguageEntryEntity> entries) throws IOException {
        Path dir = Path.of("i18n");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve("messages_" + lang.replace("-", "_") + ".properties");
        Properties properties = new Properties();
        for (LanguageEntryEntity entry : entries) {
            properties.setProperty(entry.getTextKey(), entry.getContent() == null ? "" : entry.getContent());
        }
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
        return file;
    }
}
