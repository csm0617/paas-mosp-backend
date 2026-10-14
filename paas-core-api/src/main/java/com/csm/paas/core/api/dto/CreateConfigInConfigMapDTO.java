package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `CreateConfigInConfigMapForm`（`POST /v1/config_map/{name}/config` 的请求体）：`{name, value}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateConfigInConfigMapDTO {

    private String name;
    private String value;
}
