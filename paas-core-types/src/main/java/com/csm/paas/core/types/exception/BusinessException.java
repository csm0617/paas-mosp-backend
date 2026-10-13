package com.csm.paas.core.types.exception;

import com.csm.paas.core.types.enums.ResponseCode;
import lombok.Getter;

/** 业务异常：Trigger 层捕获后按契约转成错误响应。 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResponseCode responseCode;

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getInfo());
        this.responseCode = responseCode;
    }

    public BusinessException(ResponseCode responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }
}
