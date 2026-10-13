package com.csm.paas.core.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码。取值对齐厂商后端实测到的语义（0 = OK；401 = 未认证），
 * 完整契约以 docs/findings/02-contract-baseline.md 为准。
 */
@Getter
@AllArgsConstructor
public enum ResponseCode {

    SUCCESS(0, "OK"),
    ILLEGAL_PARAMETER(400, "参数不合法"),
    UNAUTHENTICATED(401, "未认证"),
    UNKNOWN_ERROR(500, "未知错误"),
    ;

    private final int code;
    private final String info;
}
