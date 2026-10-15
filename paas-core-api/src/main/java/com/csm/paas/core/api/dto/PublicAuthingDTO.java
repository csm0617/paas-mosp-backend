package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/authing` 的请求体（厂商 `controller/request/AuthingParameters.java:8-10`：
 * 只有一个 `authToken`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicAuthingDTO {

    private String authToken;
}
