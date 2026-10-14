package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 厂商 `ConfigMapList`（`GET /v1/config_map` 的响应）：`apiVersion`/`items`/`kind`/`metadata`/`total`。
 * <p>`items` 为 null 表示"带关键字但一条都没匹配上"。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapListDTO {

    private String apiVersion;
    private List<Map<String, Object>> items;
    private String kind;
    private Map<String, Object> metadata;
    private Long total;
}
