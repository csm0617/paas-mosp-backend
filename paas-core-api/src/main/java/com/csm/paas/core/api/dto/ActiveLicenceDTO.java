package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `POST /v1/licence` 的请求体（`ActiveLicenceParam`）：`{activeCode}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveLicenceDTO {

    private String activeCode;
}
