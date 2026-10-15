package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一条**服务维度**的监控曲线（厂商 `controller/response/tenx/ServiceMonitor`：`{podName, metrics}`）。
 *
 * <p>厂商出处分两种：
 * <ul>
 *   <li>`ComputerService.getServiceMonitorInfo:909-955`：`podName` 取 `metric.pod`（PromQL 里
 *       `by (pod)` 出来的标签）；</li>
 *   <li>`ComputerService.getNetWorkMonitorInfo:872-907`：`podName` 取**入参**的 podName（不是 metric 里的），
 *       而且它先把 `metric.get("pod")` 读出来赋给一个局部变量 `pods` **却从不使用**（照抄的怪点）。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceMonitorSeriesVO {

    private String podName;

    private List<MonitorSeriesVO.Point> metrics;
}
