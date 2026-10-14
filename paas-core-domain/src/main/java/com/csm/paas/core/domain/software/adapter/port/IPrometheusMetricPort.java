package com.csm.paas.core.domain.software.adapter.port;

/**
 * 容器资源 Top（厂商 `SoftwareService.topPodResource:1734-1873` 背后的 Prometheus 查询）。
 *
 * <p>厂商出处与 URL 模板（`SoftwareService:1916-1990`，地址配置 `prometheus.system-monitoring`）：
 * <ul>
 *   <li>榜单（`topk(5, …)`）：
 *     <ul>
 *       <li>`cpu`：`avg(rate(container_cpu_usage_seconds_total{docker_type='container', namespace='<ns>'}[5m])) by (pod_name,pod_namespace)`</li>
 *       <li>`mem`：`container_memory_usage_bytes{pod_name!="",namespace="<ns>"}`</li>
 *       <li>`netout`：`rate(container_network_transmit_bytes_total{pod_name!="",namespace="<ns>"}[5m])`</li>
 *       <li>`netin`：`rate(container_network_receive_bytes_total{…}[5m])`</li>
 *       <li>`diskread`：`rate(container_fs_reads_bytes_total{…}[5m])`</li>
 *       <li>`diskwrite`：`rate(container_fs_writes_bytes_total{…}[5m])`</li>
 *     </ul>
 *     都是 `topk(5, sum by (pod_name,pod_namespace) (…))`。</li>
 *   <li>单 pod 补齐（`getOtherType*`）：
 *     <ul>
 *       <li>`cpu`：`avg(rate(container_cpu_usage_seconds_total{pod_name=~"^<pod>.*$",docker_type='container', namespace='<ns>'}[5m])) by (pod_name,pod_namespace)*<cpuNum>`</li>
 *       <li>`mem`：`container_memory_usage_bytes{pod_name=~"^<pod>.*$",namespace="<ns>"}`</li>
 *       <li>`netout`：`rate(container_network_transmit_bytes_total{pod_name=~"<pod>.*$",namespace="<ns>"}[5m])`（**注意这个没有 `^`**，照抄）</li>
 *       <li>`netin`：`rate(container_network_receive_bytes_total{pod_name=~"^<pod>.*$",…}[5m])`</li>
 *       <li>`diskread`：`rate(container_fs_reads_bytes_total{pod_name=~"^<pod>.*$",…}[5m])`</li>
 *       <li>`diskwrite`：`rate(container_fs_writes_bytes_total{pod_name=~"^<pod>.*$",…}[5m])`</li>
 *     </ul>
 *   </li>
 * </ul>
 * 解析规则（`getResourceMetric`）：`data.result[i]` → `{podName=metric.pod_name, value=value[1], namespace=metric.pod_namespace}`；
 * **任何异常都只记日志并返回空列表**（所以榜单查不到数据时这一项补 "0"）。
 *
 * <p><b>类 C：本环境不可验证</b>（本机没有 Prometheus / metrics-server；厂商默认 `http://localhost:30003`）。
 */
public interface IPrometheusMetricPort {

    /**
     * 查 Prometheus `api/v1/query`（GET，10 秒超时）。
     *
     * @param query 完整 PromQL（调用方已把命名空间/CPU 核数拼进去）
     * @return 解析后的结果列表；**URL 非 200、body 不是预期 JSON、字段缺失 ⇒ 空列表**（照抄厂商的 catch-all）
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO> query(String query);
}
