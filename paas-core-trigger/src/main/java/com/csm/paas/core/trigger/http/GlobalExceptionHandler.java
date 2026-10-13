package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.types.exception.BusinessException;
import com.csm.paas.core.types.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/** 把领域异常翻译成厂商的响应语义。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 厂商未认证：HTTP 401 + {"msg":"未认证","code":401}。 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ResponseDTO<Void>> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ResponseDTO.fail(401, "未认证"));
    }

    /** 厂商业务错误：HTTP 400 + code + msg + data.trace（堆栈原文）。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> business(BusinessException e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResponseDTO.<Map<String, Object>>builder()
                        .msg(e.getMessage())
                        .code(e.getResponseCode().getCode())
                        .data(Map.of("trace", writer.toString()))
                        .build());
    }
}
