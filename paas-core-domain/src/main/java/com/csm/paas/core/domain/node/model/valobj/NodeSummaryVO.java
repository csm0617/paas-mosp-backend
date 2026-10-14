package com.csm.paas.core.domain.node.model.valobj;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 集群里一个节点的只读投影。
 *
 * <p>来源：厂商用 `coreV1Api.listNode(...)` 读原始对象后，代码里散着取
 * `metadata.name` / `metadata.labels` / `status.addresses`（InternalIP、Hostname）/
 * `status.nodeInfo.kubeletVersion` / `status.allocatable.cpu`。
 * 域层只需要这几项，所以在端口边界一次性投影出来（读法出处见各 getter 注释）。
 */
@Data
@Builder
public class NodeSummaryVO {

    /** `metadata.name`（厂商 `existNodeName:4793`、`getNodeDisk:2118`）。 */
    private String name;

    /** `status.addresses[type=InternalIP].address`（厂商 `K8sObjectUtil.getNodeIpInfo:131-133`）。 */
    private String internalIp;

    /** `status.addresses[type=Hostname].address`（同上 `:134-136`）。 */
    private String hostname;

    /**
     * `status.nodeInfo.kubeletVersion`（厂商 `NodeVersionServiceImpl.detectNodeVersion:59-62`，
     * 判据是 `contains("k3s")`）。
     */
    private String kubeletVersion;

    /** `metadata.labels` 原样（厂商 `K8sObjectUtil.getNodeIpInfo:122`、`PubVipController:150`）。 */
    private Map<String, String> labels;

    /**
     * `status.allocatable["cpu"]` 的整数值（厂商 `ComputerService.getHostDetail:772-775`
     * 用 `quantity.getNumber().intValue()`）。
     */
    private Integer cpuAllocatable;

    /** `status.addresses` 的全部 address（厂商 `K8sObjectUtil.getNodeIpInfo:129`）。 */
    private List<String> addresses;
}
