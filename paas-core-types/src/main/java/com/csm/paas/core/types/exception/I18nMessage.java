package com.csm.paas.core.types.exception;

/**
 * 带 i18n 键的领域异常（与厂商 `WrongArgumentException` / `SystemException` 同构）。
 * <p>厂商的响应体里 {@code msg} 是**本地化后的文案**而不是键，见
 * {@code docs/findings/24-error-contract.md}；键与参数由 Trigger 层解析。
 */
public interface I18nMessage {

    /** 厂商消息包里的键（如 {@code user.username.already.exists}）。 */
    String getI18nKey();

    /** 消息参数（对应 {@code MessageFormat} 的 {0}、{1}…）。 */
    Object[] getArgs();
}
