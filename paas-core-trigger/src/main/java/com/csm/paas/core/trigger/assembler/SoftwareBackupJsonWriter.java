package com.csm.paas.core.trigger.assembler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 软件整包导出/导入的 JSON 读写（厂商 `JsonConfig.GSON` / `JsonConfig.JACKSON` 的等价物）。
 *
 * <p>照抄 GSON 的两条默认行为里**能对齐的那条**：`Gson` 默认**不输出 null 字段**（这里用
 * {@code NON_NULL} 对齐）。第 2 条（默认做 HTML 转义，`&lt;`/`&gt;`/`&amp;` 等写成 `\u003c`）**没有**对齐
 * ——这个导出文件是给"下载再上传"用的，解析按 key；差异记录在回报里。
 *
 * <p>字段名不再二次映射：domain 的 `SoftwareBackupExportEntity` 字段名就是厂商 GSON 的键名
 * （含 `serviceKeyAndName` 这个与 Java 字段名不同的键）。
 */
public final class SoftwareBackupJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private SoftwareBackupJsonWriter() {
    }

    /** 序列化成字节（导出响应用的紧凑 JSON，与 `JsonConfig.GSON.toJson` 一致无缩进）。 */
    public static byte[] toBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.csm.paas.core.types.exception.SystemException("json.serialize.failed");
        }
    }

    /** 序列化成字符串（写 zip 条目用）。 */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.csm.paas.core.types.exception.SystemException("json.serialize.failed");
        }
    }

    /** 反序列化（导入 zip 里的 software_*.json 与用户上传的 JSON 文件用）。 */
    public static <T> T fromJson(String json, Class<T> type) throws java.io.IOException {
        return MAPPER.readValue(json, type);
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
