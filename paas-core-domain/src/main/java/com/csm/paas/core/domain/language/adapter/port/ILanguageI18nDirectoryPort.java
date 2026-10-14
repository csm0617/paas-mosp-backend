package com.csm.paas.core.domain.language.adapter.port;

/**
 * `./i18n` 目录（厂商 `setDefaultVersionLang:469-483`：切回 `default` 版本时**删掉整个目录**，
 * 让语言包回到 jar 内置资源）。
 */
public interface ILanguageI18nDirectoryPort {

    /** 目录是否存在。 */
    boolean exists();

    /** 删除目录（厂商 `FileUtil.del`；失败 ⇒ 抛 `WrongArgumentException("globalization.file.delete.failed")`）。 */
    void delete();
}
