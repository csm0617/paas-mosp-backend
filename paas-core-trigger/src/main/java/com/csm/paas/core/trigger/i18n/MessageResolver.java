package com.csm.paas.core.trigger.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 把 i18n 键解析成文案（厂商契约里 {@code msg} 是文案不是键）。
 * <p>与厂商 {@code ResponseExceptionConfig.i18n(...)} 一致：**解析失败回退成键本身**。
 * <p>消息包是厂商的只读契约数据，见 {@code paas-core-trigger/src/main/resources/i18n/README.md}。
 */
@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String resolve(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args == null ? new Object[0] : args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return key;
        }
    }
}
