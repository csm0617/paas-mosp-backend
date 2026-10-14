package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `POST /v1/service/{service}/updateVolumes` 的入参（厂商 `UpdateServiceConfigmapForm`）。
 * <p>规格见 `docs/findings/119`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceVolumesVO {

    private List<ConfigMapItemVO> configMapList;
    private List<HostPathItemVO> hostPathList;

    /** 厂商 `UpdateServiceConfigmapItem`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfigMapItemVO {
        private String dir;
        private String configmap;
        private List<String> keys;
    }

    /** 厂商 `UpdateServiceHostPathForm`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HostPathItemVO {
        private String hostPath;
        private String podPath;
        private Integer readOnly;
    }
}
