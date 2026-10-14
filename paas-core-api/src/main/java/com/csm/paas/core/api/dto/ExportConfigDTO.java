package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `ExportConfig`（`POST /v1/software/existConfigmap` 与 `exportConfigmap` 的请求体）：
 * `{type, key}`——`type` 为 `software`/`service`，`key` 是对应的 id / Deployment 名列表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExportConfigDTO {

    private String type;
    private List<String> key;
}
