package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `manifest.json` 的 `contents` 一项（厂商 `model/ExportManifest$ContentEntry`）。
 * <p>`type` 取厂商 {@code ContentType} 的枚举名：`CONFIG`/`DATA`/`DOCKER_IMAGE`/`SQL_FILE`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExportManifestContentDTO {

    private String type;
    private String path;
    private String description;
    private String sourceName;
    private String databaseName;
    private String databaseRelateService;
}
