package com.csm.paas.core.types.exception;

/** 未认证：对应厂商 `{"msg":"未认证","code":401}`。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("未认证");
    }
}
