package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 软件备份聚合（厂商 `controller/response/ExportSoftwareRelationService`）。
 *
 * <p>GSON 输出键名（`@SerializedName`）：
 * `softwareFormList` / `deploymentList` / `serviceList` / `whitelistPaths` / `serviceKeyAndName`
 * ——注意最后一个：字段名是 `serviceKeyAndNameMap`，但序列化键是 **`serviceKeyAndName`**
 * （Jackson 侧靠响应的 DTO 用同名属性对齐）。
 *
 * <p>`deploymentList`/`serviceList` 是**K8s 原样对象**，在 domain 里保持 `Map&lt;String,Object&gt;`
 * （domain 不引 fabric8），由 trigger 直接用 Jackson 序列化。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareBackupExportEntity {

    @Builder.Default
    private List<SoftwareBackupFormEntity> softwareFormList = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> deploymentList = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> serviceList = new ArrayList<>();

    @Builder.Default
    private List<String> whitelistPaths = new ArrayList<>();

    @Builder.Default
    private Map<String, String> serviceKeyAndName = new LinkedHashMap<>();
}
