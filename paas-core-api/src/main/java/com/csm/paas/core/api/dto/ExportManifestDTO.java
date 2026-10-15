package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 软件整包 v2 导出的 `manifest.json`（厂商 `model/ExportManifest`，4 键 +
 * `contents`/`apps` 两个数组）。字段名必须逐字一致。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExportManifestDTO {

    @Builder.Default
    private Integer version = 1;
    private String time;
    @Builder.Default
    private List<ExportManifestContentDTO> contents = new ArrayList<>();
    @Builder.Default
    private List<ExportManifestAppDTO> apps = new ArrayList<>();
}
