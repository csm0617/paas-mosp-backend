package com.csm.paas.core.types.exception;

import lombok.Getter;

/**
 * 系统/权限类失败：对应厂商 {@code SystemException} → **HTTP 500**，
 * 响应体 {@code {"msg":<i18n 文案>,"code":500,"data":{"trace":…,"message":…}}}。
 * <p>注意与厂商"未捕获异常"那条路径区分：后者是 {@code code:-1} + {@code msg:"系统错误"}（HTTP 500）。
 * <p>厂商出处：{@code recovered/gaop-be/.../config/ResponseExceptionConfig.java} 的 {@code systemError}。
 */
@Getter
public class SystemException extends RuntimeException implements I18nMessage {

    private final String i18nKey;
    private final Object[] args;

    public SystemException(String i18nKey, Object... args) {
        super(i18nKey);
        this.i18nKey = i18nKey;
        this.args = args == null ? new Object[0] : args;
    }
}
