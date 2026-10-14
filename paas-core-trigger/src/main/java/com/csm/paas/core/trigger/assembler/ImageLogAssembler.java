package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 镜像上传日志 → 厂商契约键。
 *
 * <p>厂商 `model/ImageLog` **没有任何 Jackson 注解**，所以键就是 getter 名（全 camelCase），
 * 顺序 = 字段声明顺序（`ImageLog.java:7-15`）：
 * `id` → `fileName` → `name` → `version` → `status` → `createdAt` → `updatedAt` → `username` → `log`。
 */
public final class ImageLogAssembler {

    private ImageLogAssembler() {
    }

    public static Map<String, Object> toItem(ImageLogVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("fileName", item.getFileName());
        map.put("name", item.getName());
        map.put("version", item.getVersion());
        map.put("status", item.getStatus());
        map.put("createdAt", item.getCreatedAt());
        map.put("updatedAt", item.getUpdatedAt());
        map.put("username", item.getUsername());
        map.put("log", item.getLog());
        return map;
    }
}
