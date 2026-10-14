package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 网卡信息；照抄厂商 `service/vip/InterfaceInfo.java:10-21`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceInfoVO {

    private Integer index;
    private String name;
    private String hardwareAddr;
    private String flags;
    private List<String> addresses;
}
