package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个 Pod 的归属信息（厂商 `controller/response/BelongPodInfo`，3 个键）。
 *
 * <p>出处：`ServiceController.getServicesItems:1742-1762`（`GET /v1/service/serviceInfoByNode` 的第一步：
 * 列 pod → 按**第一个容器的名字**归组成服务，同时记下 `spec.nodeName` 与
 * `status.containerStatuses[0].restartCount`）。
 *
 * <p>注意与 {@link ServiceNodePodVO}（厂商 `ServiceBelongPodInfo`，8 个键的**资源统计**）不是同一个类，
 * 厂商那边也是两个类：前者是"pod 属于哪个服务/节点"，后者是"该服务的 cpu/memory 统计"。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceNodeBelongPodVO {

    /** `metadata.name`（仅作为 map 的 key 使用，厂商没把它塞进对象里）。 */
    private String podName;

    /** `spec.nodeName`。 */
    private String nodeName;

    /** 第一个容器的名字（= 服务名 / Deployment 名）。 */
    private String serviceName;

    /** `status.containerStatuses[0].restartCount`（没有容器状态时保持 **0**）。 */
    private Integer restartCount;
}
