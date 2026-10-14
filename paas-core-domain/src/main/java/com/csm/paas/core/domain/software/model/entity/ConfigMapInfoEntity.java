package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `ConfigMapInfo{configMapName, config}`（`GET /v1/config_map/relationService`）：
 * `configMapName` 是 ConfigMap 的 **data key**（作为 subPath 用），`config` 是引用它的服务。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapInfoEntity {

    private String configMapName;
    private List<RelationServiceEntity> config;

    /** 厂商 `RelationServiceInfo{serviceName, mountPath}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RelationServiceEntity {
        private String serviceName;
        private String mountPath;
    }
}
