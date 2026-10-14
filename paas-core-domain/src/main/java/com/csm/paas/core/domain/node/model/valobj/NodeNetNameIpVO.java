package com.csm.paas.core.domain.node.model.valobj;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 网卡名 + 该网卡下的 ip；照抄厂商 `controller/response/NetNameCoverIpInfo.java:8-10`。
 */
@Data
@Builder
public class NodeNetNameIpVO {

    private String netName;
    private List<String> ipList;
}
