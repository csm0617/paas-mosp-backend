package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `NodePortUsedInfo{name, port}`（`GET /v1/pub/nodePortUsed`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodePortUsedVO {

    private String name;
    private Integer port;
}
