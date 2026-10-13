package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import com.csm.paas.core.types.exception.BusinessException;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.UnauthorizedException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把领域异常翻译成厂商的响应语义。
 * <p>契约（实测样本 {@code docs/findings/runtime-samples/v1_lang_detail.json} + 厂商
 * {@code ResponseExceptionConfig.java}）：
 * <ul>
 *   <li>{@code WrongArgumentException} → HTTP 400，{@code {msg:<i18n 文案>, code:400, data:{trace, message}}}</li>
 *   <li>{@code SystemException} → HTTP 500，{@code {msg:<i18n 文案>, code:500, data:{trace, message}}}</li>
 *   <li>未认证 → HTTP 401，{@code {msg:"未认证", code:401}}</li>
 *   <li>其余未捕获异常 → HTTP 500，{@code {msg:"系统错误", code:-1, data:{trace, message}}}
 *       （厂商实测：缺 {@code lang} 参数就走的这条路径）</li>
 * </ul>
 * <p><b>404/405 等 Spring 框架级错误不套信封</b>：实测厂商对未映射路径返回的是 Spring 默认错误体
 * （{@code {"timestamp","status","error","message","path"}}，HTTP 404），见
 * {@code docs/findings/24-error-contract.md}。厂商跑在 Spring 5.3 上，未映射路径不会抛异常所以躲过了它的
 * {@code Throwable} 兜底；我们跑在 Spring 6.1 上会抛 {@link ErrorResponse}，因此必须显式还原该行为。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SYSTEM_EXCEPTION_KEY = "system.exception";
    private static final String UNAUTHORIZED_KEY = "user.unauthorized";
    private static final DateTimeFormatter SPRING_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Resource
    private MessageResolver messageResolver;

    /** 厂商未认证：HTTP 401 + {"msg":"未认证","code":401}。 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ResponseDTO<Void>> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ResponseDTO.fail(401, messageResolver.resolve(UNAUTHORIZED_KEY)));
    }

    /** 厂商 {@code WrongArgumentException}：HTTP 400。 */
    @ExceptionHandler(WrongArgumentException.class)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> wrongArgument(WrongArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, 400,
                messageResolver.resolve(e.getI18nKey(), e.getArgs()), e);
    }

    /** 厂商 {@code SystemException}：HTTP 500 + code 500。 */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> system(SystemException e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, 500,
                messageResolver.resolve(e.getI18nKey(), e.getArgs()), e);
    }

    /** 本仓既有形态的业务错误：HTTP 400 + msg + data.trace/message。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> business(BusinessException e) {
        return error(HttpStatus.BAD_REQUEST, e.getResponseCode().getCode(), e.getMessage(), e);
    }

    /**
     * 未捕获异常：HTTP 500 + code -1 + msg "系统错误"（与厂商 {@code exception()} 一致）。
     * <p><b>只有 404 类保留 Spring 默认错误体</b>（{@link NoResourceFoundException}/{@link NoHandlerFoundException}）——
     * 实测厂商对未映射路径返回 Spring 默认 404 体。
     * <p><b>405/415 等"路径匹配但方法/类型不对"必须走 -1 信封</b>：厂商跑在 Spring 5.3 上，
     * 这类异常是从 `handleNoMatch` 抛出来的，被它的 `Throwable` 兜底吃掉 →
     * **HTTP 500 + code -1 + msg "系统错误"**（2026-10-14 实测 `DELETE /v1/user`、`PUT /v1/user`、`PATCH /v1/role` 三条）。
     * 一开始我把所有 `ErrorResponse` 都还原成 Spring 默认体，结果 405 变成 405——与厂商不同，已按实测改正。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e, HttpServletRequest request) {
        if (e instanceof NoResourceFoundException || e instanceof NoHandlerFoundException) {
            return springDefaultError((ErrorResponse) e, request);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, -1, messageResolver.resolve(SYSTEM_EXCEPTION_KEY), e);
    }

    private ResponseEntity<Map<String, Object>> springDefaultError(ErrorResponse e, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).format(SPRING_TS));
        body.put("status", e.getStatusCode().value());
        body.put("error", HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase());
        body.put("message", "");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    private ResponseEntity<ResponseDTO<Map<String, Object>>> error(HttpStatus status, int code, String msg,
                                                                  Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        // 厂商 data 的键序是 trace 在前、message 在后（实测样本）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace", writer.toString());
        data.put("message", msg);
        return ResponseEntity.status(status)
                .body(ResponseDTO.<Map<String, Object>>builder().msg(msg).code(code).data(data).build());
    }
}
