package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `EncryptForm`（`POST /v1/licence/updateFramework` 的请求体）：`{encryptInfo}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EncryptDTO {

    private String encryptInfo;
}
