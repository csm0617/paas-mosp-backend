package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `UpdateServiceConfigmapForm`（`POST /v1/service/{service}/updateVolumes` 的请求体）。
 * <p>厂商的 form 类**没有 `@JsonProperty`** ⇒ 字段名就是 JSON 键名。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceVolumesDTO {

    private List<ConfigMapItemDTO> configMapList;
    private List<HostPathItemDTO> hostPathList;

    /** 厂商 `UpdateServiceConfigmapItem`：把一个 configmap 的若干 key 挂到 `dir` 下。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfigMapItemDTO {
        private String dir;
        private String configmap;
        private List<String> keys;
    }

    /** 厂商 `UpdateServiceHostPathForm`：把宿主路径挂到容器路径；`readOnly == 2` ⇒ 只读。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HostPathItemDTO {
        private String hostPath;
        private String podPath;
        private Integer readOnly;
    }
}
