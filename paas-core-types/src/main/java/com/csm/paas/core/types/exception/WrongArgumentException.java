package com.csm.paas.core.types.exception;

import lombok.Getter;

/**
 * 参数/前置条件不满足：对应厂商 {@code WrongArgumentException} → **HTTP 400**，
 * 响应体 {@code {"msg":<i18n 文案>,"code":400,"data":{"trace":…,"message":…}}}。
 * <p>厂商出处：{@code recovered/gaop-be/.../config/ResponseExceptionConfig.java} 的 {@code wrongArg}。
 */
@Getter
public class WrongArgumentException extends RuntimeException implements I18nMessage {

    private final String i18nKey;
    private final Object[] args;

    public WrongArgumentException(String i18nKey, Object... args) {
        super(i18nKey);
        this.i18nKey = i18nKey;
        this.args = args == null ? new Object[0] : args;
    }
}
