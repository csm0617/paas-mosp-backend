package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `POST /v1/software/software/active` 的请求体（`ActiveCode`）：`{authToken}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveCodeDTO {

    private String authToken;
}
