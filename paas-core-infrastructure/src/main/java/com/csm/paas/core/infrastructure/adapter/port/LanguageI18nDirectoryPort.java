package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.language.adapter.port.ILanguageI18nDirectoryPort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * `./i18n` 目录操作（厂商用 Hutool `FileUtil.del`；这里用 NIO 递归删除）。
 * <p>路径是**相对工作目录**的 `./i18n`（照抄厂商）。
 */
@Slf4j
@Component
public class LanguageI18nDirectoryPort implements ILanguageI18nDirectoryPort {

    private static final Path I18N_DIR = Path.of("i18n");

    @Override
    public boolean exists() {
        return Files.exists(I18N_DIR);
    }

    @Override
    public void delete() {
        try (var walk = Files.walk(I18N_DIR)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new WrongArgumentException("globalization.file.delete.failed");
                }
            });
        } catch (IOException e) {
            throw new WrongArgumentException("globalization.file.delete.failed");
        }
    }
}
