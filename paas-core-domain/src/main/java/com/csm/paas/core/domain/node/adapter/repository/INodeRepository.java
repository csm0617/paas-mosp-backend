package com.csm.paas.core.domain.node.adapter.repository;

import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;

import java.util.List;
import java.util.Optional;

/**
 * 节点只读仓储端口。
 *
 * <p>厂商侧对应的不是一张表，而是两处外部读：
 * <ul>
 *   <li>集群：`coreV1Api.listNode(...)`（`SoftwareService.existNodeName:4793`、
 *       `K8sObjectUtil.getNodeIpInfo:116`、`ComputerService.getNodeDisk:2108`）；</li>
 *   <li>Prometheus：`GET {prometheus.system-monitoring}/api/v1/query?query=...`
 *       （`ComputerService.getNodeDisk:2124-2145`）。</li>
 * </ul>
 * 实现放 `infrastructure/adapter/repository/NodeRepository`（照厂商"读"归仓储的口径）。
 */
public interface INodeRepository {

    /** 全部节点；读失败抛运行时异常（厂商 `K8sObjectUtil.getNodeIpInfo` 抛 `k8s.get.node.info.failed`）。 */
    List<NodeSummaryVO> listNodes();

    /**
     * 一次性 Prometheus 查询，取 `data.result[0].value[1]`。
     *
     * <p>照抄厂商的**取数口径**（`ComputerService.getNodeDisk:2132-2144`）：
     * 只看 `result` 第一个元素、只读 `value` 数组第 2 项；没有结果或解析失败 ⇒ 空，
     * 由调用方决定落成什么值（厂商那里是 `diskInfo.setSize(0L)` / 不设值）。
     *
     * @param query 已拼好的 PromQL（厂商把 URL 与 query 一起拼，这里只收 query，URL 由实现从配置取）
     */
    Optional<Double> queryPrometheusScalar(String query);
}
