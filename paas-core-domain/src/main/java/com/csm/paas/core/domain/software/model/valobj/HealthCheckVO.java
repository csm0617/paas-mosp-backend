package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `PUT /v1/service/healthCheck` 的入参（厂商 `HealthCheckForm` / `HealthSetInfo`）。规格见 `docs/findings/121`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthCheckVO {

    private String name;
    /** null 表示"readiness 探针不动"。 */
    private HealthSetVO readiness;
    /** null 表示"liveness 探针不动"。 */
    private HealthSetVO liveness;

    /** 厂商 `HealthSetInfo`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HealthSetVO {
        /** 0 = 清空探针；1 = HTTPGet；2 = TCPSocket；**其它值（含 null）什么都不做**。 */
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
