package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `ConfigMapInfo{configMapName, config}`（`GET /v1/config_map/relationService`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapInfoDTO {

    private String configMapName;
    private List<RelationServiceInfoDTO> config;

    /** 厂商 `RelationServiceInfo{serviceName, mountPath}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RelationServiceInfoDTO {
        private String serviceName;
        private String mountPath;
    }
}
