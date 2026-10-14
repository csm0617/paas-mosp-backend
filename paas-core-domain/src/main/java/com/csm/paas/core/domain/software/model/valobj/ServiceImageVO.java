package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `PUT /v1/service/rollingUpdate` 的入参（厂商 `ServiceImageForm`）。规格见 `docs/findings/122`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceImageVO {

    private String name;

    /** 1 ⇒ `maxSurge=1/maxUnavailable=0`；2 ⇒ `0/1`；其它值不改策略。 */
    private Integer type;

    /** 新镜像。 */
    private String version;
}
