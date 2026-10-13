package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商契约信封：{"msg": "...", "code": 0, "data": ...}。
 * <p>字段名必须与厂商一致（见 docs/findings/runtime-samples/README.md）；
 * 失败响应的 data 里厂商会带 Java 堆栈 trace，本类不省略。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseDTO<T> {

    private String msg;
    private Integer code;
    private T data;

    public static <T> ResponseDTO<T> ok(T data) {
        return ResponseDTO.<T>builder().msg("OK").code(0).data(data).build();
    }

    public static <T> ResponseDTO<T> fail(int code, String msg) {
        return ResponseDTO.<T>builder().msg(msg).code(code).data(null).build();
    }
}
