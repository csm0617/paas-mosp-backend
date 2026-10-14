package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新增节点连通性测试结果；照抄厂商 `controller/response/ConnectInfo.java:10-16`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeConnectInfoVO {

    private Boolean connect;
    private String hostname;
    private List<NodeNetNameIpVO> netWorkInfo;
}
