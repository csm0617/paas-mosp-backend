package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.node.model.valobj.NodeAlertSettingVO;
import com.csm.paas.core.domain.node.model.valobj.NodeDiskVO;
import com.csm.paas.core.domain.node.model.valobj.NodeMonitorVO;
import com.csm.paas.core.domain.node.model.valobj.NodeUsageVO;
import com.csm.paas.core.domain.node.model.valobj.ServiceMonitorVO;

import java.util.Date;
import java.util.List;

/**
 * 公共 API - 计算机资源（厂商 `controller/pub/PublicComputerController.java` 里的
 * `/nodeDisk`、`/getNodeAlertSetting`、`/setNodeAlertSetting`，以及本轮的
 * `/nodeMonitor`、`/serviceMonitor`、`/nodeUsage`）。
 *
 * <p>厂商实现：`service/ComputerService.java`
 * {@code getNodeDisk:2106-2155}、{@code setNodeAlertSetting:2157-2160}、{@code getNodeAlertSetting:2162-2180}、
 * {@code getHostDetail:693-752}、{@code getServiceDetail:968-1005}、{@code getNodeUsage:1441-1509}。
 *
 * <p><b>类 C</b>：{@code nodeDisk} 与下面三条监控端点都依赖 Prometheus（本环境不可达，代码齐全不验证）。
 */
public interface IComputerResourceService {

    /** 所有节点的磁盘总量（Prometheus `node_filesystem_size_bytes` 求和）。 */
    List<NodeDiskVO> getNodeDisk();

    /** 写 `system.node.alert.setting`（默认 `{80,80,80}`）。 */
    void setNodeAlertSetting(NodeAlertSettingVO nodeAlertSetting);

    /** 读 `system.node.alert.setting`；键不存在时**先落库默认值再读回**（厂商 `:2166-2168`）。 */
    NodeAlertSettingVO getNodeAlertSetting();

    /**
     * 节点监控（厂商 `GET /v1/pub/nodeMonitor` → `ComputerService.getHostDetail:693-752`）。
     *
     * <p>逐条照抄：
     * <ol>
     *   <li>`start`/`end` 为 null ⇒ 用"现在"与"1 小时前"（厂商 `:700-707`）；</li>
     *   <li>读节点 `status.allocatable["cpu"]` 的整数值，**cpu 曲线的每个采样点都乘这个核数**
     *       （`:718-734`；`floatValue` 与 `value` 都被改写成乘积）；</li>
     *   <li>6 条 `api/v1/query_range`（cpu/memory/网络进出/磁盘读写）+ 4 条 TCP 即时指标，
     *       PromQL 逐字照抄（`:796-837`、`:741-744`）；</li>
     *   <li>最后给每条曲线补一个"起点"采样点（值全 null，时间戳 = 查询起点，见
     *       `addStartValueToMonitorInfoDetail:781-794`）。</li>
     * </ol>
     *
     * <p><b>照抄的怪点（会 500/400）</b>：
     * <ul>
     *   <li>Prometheus 查不到数据时 `getMonitorInfo` 返回的 `metrics` 是 **null**（不是空数组），
     *       紧接着 `cpuMonitor.getMetrics().forEach(...)` ⇒ **NPE** ⇒ 外层 catch ⇒
     *       400 `k8s.get.cluster.resource.info.failed`。这里不兜；</li>
     *   <li>节点不存在（或没有 `allocatable.cpu`）同样落进那个 catch ⇒ 同一个 400。</li>
     * </ul>
     *
     * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
     *
     * @param nodeName 节点名（厂商参数 `name`）
     * @param start    查询起点；null ⇒ 1 小时前
     * @param end      查询终点；null ⇒ 现在
     */
    NodeMonitorVO nodeMonitor(String nodeName, Date start, Date end);

    /**
     * 服务监控（厂商 `GET /v1/pub/serviceMonitor` → `ComputerService.getServiceDetail:968-1005`）。
     *
     * <p>逐条照抄：
     * <ol>
     *   <li>`podName` 非空白 ⇒ 只查这个 pod（`podNames` 单元素），否则按 **Deployment 的第一个 label entry**
     *       拼出选择器、逐个 pod 查（`:973-976`、`:1176-1216`）；</li>
     *   <li>`start`/`end` 为 null ⇒ 1 小时窗口（厂商 `DateTimeUtils.convertToPrometheus:34-45`）；</li>
     *   <li>cpu/memory/网络进出/磁盘读写 6 条曲线 + 起点补齐；</li>
     *   <li>`getCpuNumByService:1007-1075` 在 try **外面**被调用，**它的返回值在查询里根本没被用到**
     *       （`getServiceCpuMonitor` 收了 `cpuNum` 参数却从不使用）——照抄，包括它"读 Deployment 失败就
     *       用空选择器列全部 pod"的副作用。</li>
     * </ol>
     *
     * <p><b>照抄的怪点</b>：
     * <ul>
     *   <li>`inboundNetwork` 实际查的是 `container_network_transmit_bytes_total`
     *       （`getInBoundNetWorkMonitor:1218-1258` 两个分支都调了 outbound 的方法）⇒ 入站=出站；</li>
     *   <li>服务名长度 &gt; 8 时网络查询用的是 `name.substring(0, 7)`（`:1177-1180`）；</li>
     *   <li>PromQL 里 `))  by (pod) ` 是**两个空格 + 行尾空格**（`:1097`），照抄。</li>
     * </ul>
     *
     * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
     *
     * @param serviceName 服务名（Deployment 名）
     * @param namespace   命名空间
     * @param podName     可选，指定单个 pod
     */
    ServiceMonitorVO serviceMonitor(String serviceName, String namespace, String podName, Date start, Date end);

    /**
     * 节点利用率（厂商 `GET /v1/pub/nodeUsage` → `ComputerService.getNodeUsage:1441-1509`）。
     *
     * <p>三组口径**各不相同**（照抄）：
     * <ul>
     *   <li><b>diskUsage</b>：逐节点查"`/` 挂载点的已用百分比"（即时查询），
     *       **取 `result[0].value[1]`**；`result` 为空 ⇒ `0.0`；查询抛异常 ⇒ `0.0`（逐节点 catch）；</li>
     *   <li><b>memoryUsage</b>：一条即时查询拿到每个节点的"已用字节数"，
     *       再除以该节点 `capacity.memory / 1024` 换算成百分比（`TenxService.getNodes:1913-1919`）；
     *       查询失败 ⇒ **500** `k8s.get.node.resource.availability.memory.status`；</li>
     *   <li><b>cpuUsage</b>：一条即时查询直接给出百分比；查询失败**直接冒异常** ⇒ 500。</li>
     * </ul>
     * 最后各自与 `system.node.alert.setting` 的三个阈值比较，超过就把 `alert` 置 true
     * （`alert` 默认 **false**，厂商 `Node` 字段初始化器）。
     *
     * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
     */
    NodeUsageVO nodeUsage();
}
