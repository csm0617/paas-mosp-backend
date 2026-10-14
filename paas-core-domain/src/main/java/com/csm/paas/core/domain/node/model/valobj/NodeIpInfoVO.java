package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 节点 ip 信息；照抄厂商 `controller/form/NodeIpInfoEntity.java:9-11`。
 * <p>`nameIpMap` 是 **ip -> hostname**（厂商 `K8sObjectUtil.getNodeIpInfo:138` `nameIpMap.put(addInfo, hostName)`，
 * `addInfo` 是 InternalIP、`hostName` 是 Hostname）。
 */
@Data
@AllArgsConstructor
public class NodeIpInfoVO {

    private String masterIp;
    private Map<String, String> nameIpMap;
}
