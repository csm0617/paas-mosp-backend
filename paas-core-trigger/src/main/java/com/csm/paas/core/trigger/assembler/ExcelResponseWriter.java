package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.trigger.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * xls 附件的响应写法（厂商 `ResponseUtils.attachment` + 各 controller 的
 * `response.setContentType("application/vnd.ms-excel")`）。
 *
 * <p>**CORS 不是通用的**：厂商只有 `SubRoleController` 的模板端点显式设了
 * `Access-Control-Allow-Origin: *` 与 `Access-Control-Expose-Headers: Content-Disposition`；
 * `RoleController` 的两个 xls 端点**都没有**。所以这个开关必须由调用方按端点给。
 */
public final class ExcelResponseWriter {

    private static final String CONTENT_TYPE = "application/vnd.ms-excel";

    private ExcelResponseWriter() {
    }

    /** 写 xls 附件：`Content-Type: application/vnd.ms-excel` + `filename*=UTF-8''<urlencoded>`。 */
    public static void write(HttpServletResponse response, byte[] bytes, MessageResolver resolver,
                             String filenameKey, boolean withCors) throws IOException {
        response.reset();
        // **不要** setCharacterEncoding：那会让 Tomcat 把 Content-Type 写成
        // `application/vnd.ms-excel;charset=UTF-8`，而厂商只有 `application/vnd.ms-excel`
        // （文件名的编码已经在 filename*=UTF-8'' 里显式给了）。
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(resolver.resolve(filenameKey), StandardCharsets.UTF_8));
        if (withCors) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        }
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }
}
