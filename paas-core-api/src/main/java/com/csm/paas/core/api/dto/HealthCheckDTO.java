package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `HealthCheckForm`（`PUT /v1/service/healthCheck` 的请求体）。
 * <p>`readiness`/`liveness` 都可为 null（为 null 表示"这个探针不动"）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthCheckDTO {

    private String name;
    private HealthSetDTO readiness;
    private HealthSetDTO liveness;

    /** 厂商 `HealthSetInfo`：`type` 0=清空 1=HTTPGet 2=TCPSocket，其它值不动。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HealthSetDTO {
        private Integer type;
        private String path;
        private Integer port;
        private Integer initialDelaySeconds;
        private Integer timeoutSeconds;
        private Integer periodSeconds;
        private Integer successThreshold;
        private Integer failureThreshold;
    }
}
