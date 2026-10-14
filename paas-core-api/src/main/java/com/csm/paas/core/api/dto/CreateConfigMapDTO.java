package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `CreateConfigMapForm`（`POST /v1/config_map` 的请求体）：只有 `name`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateConfigMapDTO {

    private String name;
}
