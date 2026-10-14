package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `UpdatePortItem`（`UpdateServiceDiscoveryForm.portList` 的元素）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePortItemVO {

    private Integer containerPort;
    private String protocol;
    private String name;
    private Integer port;
    private Integer targetPort;
}
