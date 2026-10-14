package com.csm.paas.core.trigger.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 厂商 `exportConfigMapBySoftwareOrService:3996-4014` 的等价实现：
 * 把 ConfigMap 列表用 Jackson 序列化后作为附件写回（`JsonConfig.JACKSON` 未开缩进 ⇒ **紧凑 JSON**）。
 *
 * <p>照抄点：响应头是 `application/octet-stream` + 文件名 `configmap.json`（不是 Excel 那一套）；
 * 内容是 **ConfigMap 对象的数组**（原样快照，不做裁剪）。
 */
public final class ExportConfigMapWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExportConfigMapWriter() {
    }

    public static byte[] build(List<Map<String, Object>> configMaps) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(configMaps);
    }
}
