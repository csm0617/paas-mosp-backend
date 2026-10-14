package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `ServiceImageForm`（`PUT /v1/service/rollingUpdate` 的请求体）。
 * <p>`type`：1 ⇒ `maxSurge=1, maxUnavailable=0`；2 ⇒ `maxSurge=0, maxUnavailable=1`；
 * **其它值不改策略**（但仍会改镜像）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceImageDTO {

    private String name;
    private Integer type;
    /** 新镜像（厂商直接写进 `containers[0].image`）。 */
    private String version;
}
