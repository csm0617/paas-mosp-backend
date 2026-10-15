package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个服务的 Pod 资源统计（厂商 `controller/response/ServiceBelongPodInfo`，8 个键）。
 *
 * <p>出处：`ServiceController.getPodCpuInfo:1899-1921` / `getPodMemoryInfo:1875-1897`
 * （`GET /v1/service/serviceInfoByNode` 的每服务补齐）。
 *
 * <p><b>照抄的怪点</b>：
 * <ul>
 *   <li>cpu/memory 的 max/min/avg 都是在**所有 pod 的所有采样点扁平化之后**统计的，不是"先按 pod 再统计"；
 *       而且最终只挂到**一个**对象上，`podName` 填的是 **Deployment 名**（不是 pod 名）；</li>
 *   <li>memory 用 `Long.parseLong(值)` 解析 Prometheus 的原始串：Prometheus 常见的科学计数法
 *       （如 `1.234e+08`）会抛 `NumberFormatException`，被 catch 吞掉 ⇒ **该服务的 memory 三项保持 null**；</li>
 *   <li>异常（含上面的解析失败）只记日志、不影响响应。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceNodePodVO {

    private String podName;

    private String nodeName;

    private Double cpuMaxUsed;

    private Double cpuMinUsed;

    private Double cpuAvgUsed;

    private Long memoryMaxUsed;

    private Long memoryMinUsed;

    private Double memoryAvgUsed;
}
