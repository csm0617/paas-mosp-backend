package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `CreateK8sServicesController.ValidationResult`（`CreateK8sServicesController.java:68-88`）。
 *
 * <p><b>字段名照抄厂商的序列化结果</b>：厂商那个类只有 `isValid()` / `getMessage()` 两个 getter，
 * Jackson 把布尔 getter 归一成属性名 **`valid`**（不是 `isValid`）——厂商自己的 swagger
 * （`recovered-web/regenerated/openapi-gaop-be.json` 的 `ValidationResult`）也写的是
 * `{"message": ..., "valid": ...}`。所以这里用 `boolean valid`（Lombok 生成 `isValid()`），
 * 线上 JSON 与厂商一致。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidationResultDTO {

    /** 厂商 `ValidationResult.isValid()` ⇒ JSON 键 `valid`。 */
    private boolean valid;

    /** `kubectl` 的输出（成功是 stdout，失败是合并后的 stdout+stderr）。 */
    private String message;
}
