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
 *
 * <p>本轮（最后一批端点）在这个端口上补了**范围查询**与**即时向量查询**两个能力，
 * 分别服务厂商 `ComputerService` 的监控框架与 `getNodeUsage`：
 * <ul>
 *   <li>{@link #queryRange} ← `getMonitorInfo:839-870` / `getServiceMonitorInfo:909-955` /
 *       `getNetWorkMonitorInfo:872-907` 三种解析口径共用的原始数据（`api/v1/query_range`）；</li>
 *   <li>{@link #queryVector} ← `getNodeUsage:1454-1484` / `getMemoryUsage:1511-1526` /
 *       `getCpuUsage:1528-1537`（`api/v1/query`，**异常语义与上面相反**，见方法注释）。</li>
 * </ul>
 */
public interface IPrometheusMetricPort {

    /**
     * 查 Prometheus `api/v1/query`（GET，10 秒超时）。
     *
     * @param query 完整 PromQL（调用方已把命名空间/CPU 核数拼进去）
     * @return 解析后的结果列表；**URL 非 200、body 不是预期 JSON、字段缺失 ⇒ 空列表**（照抄厂商的 catch-all）
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO> query(String query);

    /**
     * 查 Prometheus `api/v1/query_range`（GET，超时取 `prometheus.timeout`，厂商默认 **30000ms**）。
     *
     * <p>厂商出处：`ComputerService.getMonitorInfo:839-870`、`getServiceMonitorInfo:909-955`、
     * `getNetWorkMonitorInfo:872-907` 三个方法里的 `HttpRequest.get(url).timeout(prometheusConfig.getTimeout())`。
     *
     * <p><b>异常语义照抄</b>：三个方法**各自都有一段 catch-all**（`catch (Exception e) { log.error(...) }`），
     * 所以连接失败、非 200、body 不是 JSON、字段缺失 ⇒ **空列表**（不是抛异常）。
     *
     * <p><b>时间戳的照抄点</b>：厂商把 `values[j][0]` 强转成 `Integer`
     * （`((Integer)jsonArray.get(0)).intValue()`）。Prometheus 在"整秒"采样点上输出的是整数
     * （厂商 URL 里的 `start`/`end`/`step` 都是整数秒，采样点因此都落在整秒），所以能转成功；
     * 一旦出现**带小数的时间戳**，Hutool 给的是 `Double` ⇒ `ClassCastException` ⇒ 被 catch 吞掉 ⇒ 空列表。
     * 实现里用"非整数时间戳就抛"来复刻这个可观测行为。
     *
     * @param query            完整 PromQL（调用方按厂商原样拼好，含空格与双引号）
     * @param startEpochSecond 起始（**秒**，厂商用 `Date.getTime() / 1000`）
     * @param endEpochSecond   结束（**秒**）
     * @param step             步长（**秒**，厂商 `getStepBySecond:1077-1088`）
     * @return 每条时间序列的原始标签与采样点；任何解析异常 ⇒ 空列表
     */
    java.util.List<RangeSeries> queryRange(String query, long startEpochSecond, long endEpochSecond, int step);

    /**
     * 查 Prometheus `api/v1/query`（即时向量），返回 `data.result` 里每条的标签与 `value[1]`。
     *
     * <p>厂商出处：`ComputerService.getNodeUsage:1454-1484`（磁盘利用率）、
     * `getMemoryUsage:1511-1526`、`getCpuUsage:1528-1537`，以及 `getNodeList:1539-1552` 的解析。
     *
     * <p><b>与 {@link #queryRange} 的异常语义相反（照抄）</b>：这条链路上厂商**没有** catch-all——
     * `getCpuUsage` 连 try 都没有，`getMemoryUsage` 的 `catch (Exception)` 是转成
     * `SystemException("k8s.get.node.resource.availability.memory.status")`（HTTP 500）。
     * 所以这里**连接失败 / 非 200 / body 不是预期 JSON / 没有 `data.result` ⇒ 抛运行时异常**，
     * 由调用方决定翻成 500 还是"按 0 处理"（磁盘那一路是逐节点 catch 成 0.0）。
     *
     * @return `data.result` 逐条；`result` 是空数组时返回**空列表**（厂商 `result.size() == 0` 的分支）
     */
    java.util.List<VectorSample> queryVector(String query);

    /** 一条范围查询结果：`metric` 标签表 + 采样点（时间戳**秒** + 原样值字符串）。 */
    record RangeSeries(java.util.Map<String, String> metric, java.util.List<RangeSample> values) {
    }

    /** 一个采样点：`values[j] = [<秒>, "<值字符串>"]`。 */
    record RangeSample(long timestamp, String value) {
    }

    /** 一条即时查询结果：`metric` 标签表 + `value[1]` 原样值字符串。 */
    record VectorSample(java.util.Map<String, String> metric, String value) {
    }
}
