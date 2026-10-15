package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/authing` 的一行结果（厂商 `controller/response/AuthingResult.java:6-8`）。
 *
 * <p>照抄点：`result` 是 **基本类型 boolean 且默认 false** —— upsert 没影响到 1 行时保持 false
 * （所以 JSON 里永远有这个字段，不会是 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicAuthingResultDTO {

    private String serviceId;

    @Builder.Default
    private boolean result = false;
}
