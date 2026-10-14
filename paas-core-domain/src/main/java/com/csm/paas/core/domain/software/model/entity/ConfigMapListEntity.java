package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 厂商 `ConfigMapList extends IoK8sApiCoreV1ConfigMapList`（+ `total`）：
 * JSON 键为 `apiVersion`/`items`/`kind`/`metadata`/`total`。
 * <p>`items` 为 **null** 表示"带关键字但一条都没匹配上"（厂商显式 set null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapListEntity {

    private String apiVersion;
    private List<Map<String, Object>> items;
    private String kind;
    private Map<String, Object> metadata;
    private Long total;
}
