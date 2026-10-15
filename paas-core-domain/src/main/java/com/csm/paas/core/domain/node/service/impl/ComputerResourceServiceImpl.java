package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.adapter.repository.INodeRepository;
import com.csm.paas.core.domain.node.model.valobj.MonitorSeriesVO;
import com.csm.paas.core.domain.node.model.valobj.NodeAlertSettingVO;
import com.csm.paas.core.domain.node.model.valobj.NodeDiskVO;
import com.csm.paas.core.domain.node.model.valobj.NodeMonitorVO;
import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;
import com.csm.paas.core.domain.node.model.valobj.NodeUsageVO;
import com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO;
import com.csm.paas.core.domain.node.model.valobj.ServiceMonitorVO;
import com.csm.paas.core.domain.node.service.IComputerResourceService;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IPrometheusMetricPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计算机资源实现（厂商 `service/ComputerService.java`）。
 *
 * <p>本类覆盖两批端点：
 * <ul>
 *   <li>`/nodeDisk`、`/getNodeAlertSetting`、`/setNodeAlertSetting`（厂商 `:2106-2180`，第一批）；</li>
 *   <li>`/nodeMonitor`、`/serviceMonitor`、`/nodeUsage`（厂商 `getHostDetail:693-752`、
 *       `getServiceDetail:968-1005`、`getNodeUsage:1441-1509`，本轮）。</li>
 * </ul>
 *
 * <p><b>类 C</b>：Prometheus 相关的全部方法在本环境不可达（本机没有 Prometheus）；
 * PromQL 与解析口径逐字照抄，代码齐全、不做在线验证。
 *
 * <p>照抄点 / 偏差：
 * <ul>
 *   <li>PromQL 与厂商**逐字相同**（含 `))  by (pod) ` 的双空格与行尾空格）；编码由端口实现负责拼；</li>
 *   <li>取数只看 `result[0].value[1]`，四舍五入成 {@code Long}（厂商 `:2141`）；</li>
 *   <li>**没有**取到值时不设 {@code size}（保持 null），只有**抛异常**才落 {@code 0L}
 *       （厂商 `:2146-2149`）——两种情况的结果不同，照抄；</li>
 *   <li>告警设置厂商用 Hutool `JSONUtil.toJsonStr` + Jackson `ObjectMapper` 读写。
 *       域层禁用 Jackson，这里用**等价的定点文本**处理
 *       （键序 cpu/memory/disk，值用 {@code Double.toString}）。</li>
 *   <li>`getNodeUsage` 里的"节点内存总量"厂商走 `tenxService.getNodes()`（一次列全部节点 + 全部 pod）；
 *       本平台只需要 `capacity.memory`，改用 {@link IServiceRuntimePort#listNodeCapacityMemoryKb()}
 *       （同一个 `listNode` 读，去掉与响应无关的 pod 统计）。</li>
 *   <li>厂商 `getServiceDetail` 用线程池 + Caffeine 缓存的部分在 `ServiceController.serviceInfoByNode`
 *       （见 `ServiceQueryServiceImpl`）；本类只做单服务监控。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComputerResourceServiceImpl implements IComputerResourceService {

    /** 厂商 `ComputerService` 里写死的设置键（`:2158`、`:2164`）。 */
    private static final String SETTING_KEY = "system.node.alert.setting";

    private static final Pattern CPU = Pattern.compile("\"cpuUsageAlert\"\\s*:\\s*([0-9.eE+-]+)");
    private static final Pattern MEMORY = Pattern.compile("\"memoryUsageAlert\"\\s*:\\s*([0-9.eE+-]+)");
    private static final Pattern DISK = Pattern.compile("\"diskUsageAlert\"\\s*:\\s*([0-9.eE+-]+)");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final INodeRepository nodeRepository;
    private final ISettingRepository settingRepository;

    /** 监控三件套的 Prometheus 范围/即时查询端口（厂商 `prometheusConfig.getSystemMonitoring()`）。 */
    @Resource
    private IPrometheusMetricPort prometheusMetricPort;

    /** `serviceMonitor` 要读 Deployment 标签、列 pod、读 pod 的 hostIP 并回查节点核数。 */
    @Resource
    private IServiceRuntimePort serviceRuntimePort;

    @Override
    public List<NodeDiskVO> getNodeDisk() {
        List<NodeDiskVO> list = new ArrayList<>();
        List<NodeSummaryVO> nodes;
        try {
            nodes = nodeRepository.listNodes();
        } catch (RuntimeException e) {
            // 厂商 `:2113-2116`：读节点失败只记日志、返回空列表（不抛）。
            log.error("获取k8s节点信息出现异常:{}", e.getMessage(), e);
            return list;
        }
        List<String> nodeNames = new ArrayList<>();
        for (NodeSummaryVO node : nodes) {
            nodeNames.add(node.getName());
        }
        log.info("获取到所有节点的名称为：{}", nodeNames);

        for (String nodeName : nodeNames) {
            NodeDiskVO diskInfo = new NodeDiskVO();
            diskInfo.setName(nodeName);
            String query = "sum(node_filesystem_size_bytes{node=\"" + nodeName
                    + "\", fstype!~\"rootfs|tmpfs|fuse.lxcfs\", device!~\"^/dev/mapper/docker-.+\"})";
            try {
                log.debug("请求普罗米修斯：query = {}", query);
                Optional<Double> size = nodeRepository.queryPrometheusScalar(query);
                if (size.isPresent()) {
                    diskInfo.setSize(Math.round(size.get()));
                }
            } catch (RuntimeException e) {
                log.error("请求获取磁盘信息接口出错: {}", e.getMessage(), e);
                diskInfo.setSize(0L);
            }
            list.add(diskInfo);
        }
        return list;
    }

    @Override
    public void setNodeAlertSetting(NodeAlertSettingVO nodeAlertSetting) {
        // 厂商 `:2159` 用 updateOrInsert（存在则只改 val/updated_at）。
        settingRepository.upsertValue(SETTING_KEY, toJson(nodeAlertSetting), "");
    }

    @Override
    public NodeAlertSettingVO getNodeAlertSetting() {
        NodeAlertSettingVO nodeAlertSetting = NodeAlertSettingVO.builder()
                .cpuUsageAlert(80.0).memoryUsageAlert(80.0).diskUsageAlert(80.0).build();
        Optional<String> settingInfo = settingRepository.queryValue(SETTING_KEY);
        if (settingInfo.isEmpty()) {
            // 厂商 `:2166-2168`：不存在就落默认值；注意厂商**丢弃**了 upsert 的返回，
            // 紧接着仍从 settingInfo.getVal() 反序列化（此时是刚插入的 val）。
            settingRepository.upsertValue(SETTING_KEY, toJson(nodeAlertSetting), "");
            return nodeAlertSetting;
        }
        try {
            nodeAlertSetting = fromJson(settingInfo.get(), nodeAlertSetting);
        } catch (RuntimeException e) {
            log.error("获取资源告警配置反序列化异常", e);
        }
        return nodeAlertSetting;
    }

    // ================================================================ 监控三件套（最后一批端点）

    /** 厂商 `ComputerService.getHostDetail:693-752`（`GET /v1/pub/nodeMonitor`）。 */
    @Override
    public NodeMonitorVO nodeMonitor(String nodeName, Date start, Date end) {
        NodeMonitorVO nodeInfo = new NodeMonitorVO();
        log.info("获取传入的node节点名称为：{}", nodeName);
        // 厂商 `:700-707`：默认窗口是"现在往前 1 小时"。
        Instant defaultEnd = Instant.now();
        Instant defaultStart = defaultEnd.minus(1L, ChronoUnit.HOURS);
        if (start == null) {
            start = Date.from(defaultStart);
        }
        if (end == null) {
            end = Date.from(defaultEnd);
        }
        try {
            long startTime = start.getTime() / 1000L;
            long endTime = end.getTime() / 1000L;
            Integer cpuNumberInfo = allocatableCpuOf(nodeName);
            log.info("节点:{}的cpu核数为:{}", nodeName, cpuNumberInfo);
            MonitorSeriesVO cpuMonitor = cpuMonitor(nodeName, startTime, endTime);
            log.info("============cpuMonitor的内容{}", cpuMonitor);
            log.info("=========cpuMonitor.getMetrics()的内容{}", cpuMonitor.getMetrics());
            // 照抄：厂商这里不判空——Prometheus 查不到数据时 metrics 是 null ⇒ NPE ⇒ 外层 catch ⇒ 400。
            cpuMonitor.getMetrics().forEach(p -> {
                String floatValue = p.getFloatValue();
                String value = p.getValue();
                double parseDouble = floatValue != null ? Double.parseDouble(floatValue) : 0.0;
                double v = value != null ? Double.parseDouble(value) : 0.0;
                p.setFloatValue(Double.toString(parseDouble * cpuNumberInfo));
                p.setValue(Double.toString(v * cpuNumberInfo));
            });
            nodeInfo.setCpu(cpuMonitor);
            nodeInfo.setMemory(toMonitorSeries(rangeQuery(memoryQuery(nodeName), startTime, endTime)));
            nodeInfo.setInboundNetwork(toMonitorSeries(rangeQuery(inboundNetworkQuery(nodeName), startTime, endTime)));
            nodeInfo.setOutboundNetwork(toMonitorSeries(rangeQuery(outboundNetworkQuery(nodeName), startTime, endTime)));
            nodeInfo.setDiskReadIo(toMonitorSeries(rangeQuery(diskReadQuery(nodeName), startTime, endTime)));
            nodeInfo.setDiskWriteIo(toMonitorSeries(rangeQuery(diskWriteQuery(nodeName), startTime, endTime)));
            nodeInfo.setCurrEstab(tcpMonitorInfo(nodeName, "node_netstat_Tcp_CurrEstab", startTime, endTime));
            nodeInfo.setTcpTimeouts(tcpMonitorInfo(nodeName, "node_netstat_TcpExt_TCPTimeouts", startTime, endTime));
            nodeInfo.setActiveOpens(tcpMonitorInfo(nodeName, "node_netstat_Tcp_ActiveOpens", startTime, endTime));
            nodeInfo.setPassiveOpens(tcpMonitorInfo(nodeName, "node_netstat_Tcp_PassiveOpens", startTime, endTime));
            addStartValueToMonitorInfo(nodeInfo, null, startTime);
        } catch (Exception e) {
            log.error("获取集群资源信息失败", e);
            throw new WrongArgumentException("k8s.get.cluster.resource.info.failed");
        }
        return nodeInfo;
    }

    /** 厂商 `ComputerService.getServiceDetail:968-1005`（`GET /v1/pub/serviceMonitor`）。 */
    @Override
    public ServiceMonitorVO serviceMonitor(String serviceName, String namespace, String podName,
                                           Date start, Date end) {
        List<String> podNames = new ArrayList<>();
        if (!isBlank(podName)) {
            podNames.add(podName);
        }
        ServiceMonitorVO serviceMetric = new ServiceMonitorVO();
        // 照抄：厂商在 try **外面**调它，而且它的返回值在后续查询里**从未被使用**
        // （getServiceCpuMonitor 收了 cpuNum 参数却从不使用）。它的副作用（读 Deployment/pod/节点）要保留。
        Integer cpuNumByService = cpuNumByService(serviceName, namespace);
        try {
            // 厂商 DateTimeUtils.convertToPrometheus:34-45：end 默认 now、start 默认 now-1h，再按入参覆盖。
            long endTime = end != null ? end.getTime() / 1000L : System.currentTimeMillis() / 1000L;
            long startTime = start != null ? start.getTime() / 1000L : endTime - 3600L;
            serviceMetric.setCpu(serviceCpuMonitor(serviceName, startTime, endTime, podNames));
            serviceMetric.setMemory(serviceMemoryMonitor(serviceName, startTime, endTime, podNames));
            serviceMetric.setOutboundNetwork(
                    outBoundNetWorkMonitor(serviceName, namespace, podName, startTime, endTime, podNames));
            serviceMetric.setInboundNetwork(
                    inBoundNetWorkMonitor(serviceName, namespace, podName, startTime, endTime, podNames));
            serviceMetric.setDiskReadIo(serviceDiskReadIoMonitor(serviceName, startTime, endTime, podNames));
            serviceMetric.setDiskWriteIo(serviceDiskWriteIoMonitor(serviceName, startTime, endTime, podNames));
            addStartValueToMonitorInfo(null, serviceMetric, startTime);
            log.debug("cpuNumByService（照抄：未被查询使用）={}", cpuNumByService);
            return serviceMetric;
        } catch (Exception e) {
            log.error("获取服务监控资源信息失败", e);
            throw new WrongArgumentException("service.get.service.monitor.resource.failed");
        }
    }

    /** 厂商 `ComputerService.getNodeUsage:1441-1509`（`GET /v1/pub/nodeUsage`）。 */
    @Override
    public NodeUsageVO nodeUsage() {
        NodeUsageVO nodeUsageInfo = new NodeUsageVO();
        // 厂商 `:1445-1448`：listNode 的异常直接冒出去（⇒ 500）。
        List<String> nodeNames = new ArrayList<>();
        for (NodeSummaryVO node : nodeRepository.listNodes()) {
            nodeNames.add(node.getName());
        }
        log.info("获取到所有节点的名称为：{}", nodeNames);
        NodeAlertSettingVO nodeAlertSetting = getNodeAlertSetting();

        List<NodeUsageVO.Item> diskUsage = new ArrayList<>();
        for (String node : nodeNames) {
            NodeUsageVO.Item diskNode = NodeUsageVO.Item.builder().name(node).alert(false).build();
            // 厂商 `:1454` 的 PromQL 逐字照抄（含 `- ` 后的空格与 `mountpoint="/"`）。
            String query = "(sum(node_filesystem_size_bytes{node=\"" + node
                    + "\", fstype!~\"rootfs|tmpfs|fuse.lxcfs\",device!~\"^/dev/mapper/docker-.+\", mountpoint=\"/\"})- "
                    + "sum(node_filesystem_avail_bytes{node=\"" + node
                    + "\", fstype!~\"rootfs|tmpfs|fuse.lxcfs\",device!~\"^/dev/mapper/docker-.+\",mountpoint=\"/\"}))/ "
                    + "sum(node_filesystem_size_bytes{node=\"" + node
                    + "\", fstype!~\"rootfs|tmpfs|fuse.lxcfs\",device!~\"^/dev/mapper/docker-.+\", mountpoint=\"/\"})*100";
            try {
                log.debug("请求普罗米修斯：url = {}", query);
                List<IPrometheusMetricPort.VectorSample> samples = prometheusMetricPort.queryVector(query);
                if (!samples.isEmpty()) {
                    String value = samples.get(0).value();
                    if (!isBlank(value)) {
                        diskNode.setData(Double.parseDouble(value));
                    }
                } else {
                    log.error("无法拿到磁盘下的利用率信息");
                    diskNode.setData(0.0);
                }
            } catch (Exception e) {
                log.error("请求获取磁盘信息接口出错:", e);
                diskNode.setData(0.0);
            }
            // 照抄：value 是空白串时 data 仍是 null ⇒ 这里拆箱 NPE ⇒ 500。
            if (diskNode.getData() > nodeAlertSetting.getDiskUsageAlert()) {
                diskNode.setAlert(true);
            }
            diskUsage.add(diskNode);
        }
        nodeUsageInfo.setDiskUsage(diskUsage);

        List<NodeUsageVO.Item> memoryUsage = memoryUsage();
        Map<String, Long> memoryKbByNode = serviceRuntimePort.listNodeCapacityMemoryKb();
        for (NodeUsageVO.Item memoryNode : memoryUsage) {
            for (Map.Entry<String, Long> entry : memoryKbByNode.entrySet()) {
                if (entry.getKey().equals(memoryNode.getName())) {
                    // 照抄：memory_total_kb 为 null 时这里 NPE ⇒ 500（厂商同样不判空）。
                    memoryNode.setData(memoryNode.getData() / 1024.0 / entry.getValue().longValue() * 100.0);
                }
            }
            if (memoryNode.getData() > nodeAlertSetting.getMemoryUsageAlert()) {
                memoryNode.setAlert(true);
            }
        }
        nodeUsageInfo.setMemoryUsage(memoryUsage);

        List<NodeUsageVO.Item> cpuUsage = cpuUsage();
        for (NodeUsageVO.Item cpuNode : cpuUsage) {
            if (cpuNode.getData() > nodeAlertSetting.getCpuUsageAlert()) {
                cpuNode.setAlert(true);
            }
        }
        nodeUsageInfo.setCpuUsage(cpuUsage);
        return nodeUsageInfo;
    }

    // ---------------------------------------------------------------- 节点监控的 PromQL / 解析（厂商 :796-870）

    /** 厂商 `getTcpMonitorInfo:796-800`：`<metric>{node="<name>"}`。 */
    private MonitorSeriesVO tcpMonitorInfo(String name, String metric, long start, long end) {
        String query = metric + "{node=\"" + name + "\"}";
        return toMonitorSeries(rangeQuery(query, start, end));
    }

    /** 厂商 `getCpuMonitor:802-807`。 */
    private MonitorSeriesVO cpuMonitor(String name, long start, long end) {
        String query = "(1-avg(irate(node_cpu_seconds_total{node=\"" + name + "\",mode=\"idle\"}[1h])))";
        log.info("===============请求的路径为{}", query);
        return toMonitorSeries(rangeQuery(query, start, end));
    }

    /** 厂商 `getMemoryMonitor:809-813`。 */
    private String memoryQuery(String name) {
        return "(node_memory_MemTotal_bytes{node=\"" + name + "\"}-node_memory_MemAvailable_bytes{node=\"" + name
                + "\"}-node_memory_Buffers_bytes{node=\"" + name + "\"})>0";
    }

    /** 厂商 `getInboundNetworkMonitor:815-819`。 */
    private String inboundNetworkQuery(String name) {
        return "max(irate(node_network_receive_bytes_total{node=\"" + name
                + "\",device!~'tap.*|veth.*|br.*|docker.*|virbr*|lo*'}[1h]))";
    }

    /** 厂商 `getOutboundNetworkMonitor:821-825`。 */
    private String outboundNetworkQuery(String name) {
        return "max(irate(node_network_transmit_bytes_total{node=\"" + name
                + "\",device!~'tap.*|veth.*|br.*|docker.*|virbr*|lo*'}[1h]))";
    }

    /** 厂商 `getDiskReadIoMonitor:827-831`。 */
    private String diskReadQuery(String name) {
        return "sum(irate(node_disk_read_bytes_total{node=\"" + name + "\"}[1h]))";
    }

    /** 厂商 `getDiskWriteIoMonitor:833-837`。 */
    private String diskWriteQuery(String name) {
        return "sum(irate(node_disk_written_bytes_total{node=\"" + name + "\"}[1h]))";
    }

    /** 厂商 `getStepBySecond:1077-1088`（步长 = 跨度/10000 向上取整，下限 60 秒）。 */
    private static int stepBySecond(long start, long end) {
        if (start >= end) {
            return 60;
        }
        long durationMillis = end - start;
        double minStepRequiredSeconds = (double) durationMillis / 10000.0;
        int calculatedStepSeconds = (int) Math.ceil(minStepRequiredSeconds);
        if (calculatedStepSeconds < 60) {
            return 60;
        }
        return calculatedStepSeconds;
    }

    private List<IPrometheusMetricPort.RangeSeries> rangeQuery(String query, long start, long end) {
        return prometheusMetricPort.queryRange(query, start, end, stepBySecond(start, end));
    }

    /** 服务监控那几条链路自己先算了 `step`（厂商也是先 `getStepBySecond` 再拼 URL）。 */
    private List<IPrometheusMetricPort.RangeSeries> rangeQuery(String query, long start, long end, int step) {
        return prometheusMetricPort.queryRange(query, start, end, step);
    }

    /**
     * 厂商 `getMonitorInfo:839-870` 的解析口径：把所有序列的采样点**扁平化**成一个列表，
     * `value` 是 `Math.round` 后的整数字符串、`floatValue` 是原始串，
     * `latestTimestamp` 取**最后一个**采样点的时间。
     *
     * <p>照抄：`result` 为空（或解析中途出错）时厂商在 `get(size-1)` 上越界、异常被吞 ⇒
     * 返回的对象 `metrics`/`latestTimestamp` **都是 null**。
     */
    private MonitorSeriesVO toMonitorSeries(List<IPrometheusMetricPort.RangeSeries> seriesList) {
        MonitorSeriesVO monitorInfo = new MonitorSeriesVO();
        try {
            List<MonitorSeriesVO.Point> points = new ArrayList<>();
            for (IPrometheusMetricPort.RangeSeries series : seriesList) {
                for (IPrometheusMetricPort.RangeSample sample : series.values()) {
                    MonitorSeriesVO.Point point = new MonitorSeriesVO.Point();
                    point.setTimestamp(formatInstant(Instant.ofEpochSecond(sample.timestamp())));
                    point.setFloatValue(sample.value());
                    point.setValue(Math.round(Double.parseDouble(sample.value())) + "");
                    points.add(point);
                }
            }
            monitorInfo.setLatestTimestamp(points.get(points.size() - 1).getTimestamp());
            monitorInfo.setMetrics(points);
        } catch (Exception e) {
            log.error("请求获取node信息接口出错:", e);
            return new MonitorSeriesVO();
        }
        return monitorInfo;
    }

    /** 厂商 `addStartValueToMonitorInfo:754-770` + `addStartValueToMonitorInfoDetail:781-794`。 */
    private void addStartValueToMonitorInfo(NodeMonitorVO nodeInfo, ServiceMonitorVO serviceMetric, long startTime) {
        if (nodeInfo != null) {
            List<MonitorSeriesVO> monitorInfos = Arrays.asList(nodeInfo.getCpu(), nodeInfo.getMemory(),
                    nodeInfo.getInboundNetwork(), nodeInfo.getOutboundNetwork(), nodeInfo.getDiskReadIo(),
                    nodeInfo.getDiskWriteIo(), nodeInfo.getCurrEstab(), nodeInfo.getTcpTimeouts(),
                    nodeInfo.getActiveOpens(), nodeInfo.getPassiveOpens());
            for (MonitorSeriesVO monitorInfo : monitorInfos) {
                if (monitorInfo == null || monitorInfo.getMetrics() == null) {
                    continue;
                }
                addStartValueToMonitorInfoDetail(monitorInfo.getMetrics(), startTime);
            }
        }
        if (serviceMetric != null) {
            processServiceMonitorList(serviceMetric.getCpu(), startTime);
            processServiceMonitorList(serviceMetric.getMemory(), startTime);
            processServiceMonitorList(serviceMetric.getOutboundNetwork(), startTime);
            processServiceMonitorList(serviceMetric.getInboundNetwork(), startTime);
            processServiceMonitorList(serviceMetric.getDiskReadIo(), startTime);
            processServiceMonitorList(serviceMetric.getDiskWriteIo(), startTime);
        }
    }

    private void processServiceMonitorList(List<ServiceMonitorSeriesVO> serviceMonitors, long startTime) {
        if (serviceMonitors != null) {
            for (ServiceMonitorSeriesVO serviceMonitor : serviceMonitors) {
                if (serviceMonitor == null || serviceMonitor.getMetrics() == null) {
                    continue;
                }
                addStartValueToMonitorInfoDetail(serviceMonitor.getMetrics(), startTime);
            }
        }
    }

    private void addStartValueToMonitorInfoDetail(List<MonitorSeriesVO.Point> metrics, long startTime) {
        String startStr = formatInstant(Instant.ofEpochSecond(startTime));
        if (!metrics.isEmpty() && startStr.compareTo(metrics.get(0).getTimestamp()) < 0) {
            MonitorSeriesVO.Point first = metrics.get(0);
            first.setFloatValue(null);
            first.setValue(null);
            first.setTimestamp(first.getTimestamp());
            MonitorSeriesVO.Point startPoint = new MonitorSeriesVO.Point();
            startPoint.setFloatValue(null);
            startPoint.setValue(null);
            startPoint.setTimestamp(startStr);
            metrics.add(0, startPoint);
        }
    }

    private static String formatInstant(Instant instant) {
        return DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }

    // ---------------------------------------------------------------- 服务监控（厂商 :968-1258）

    /**
     * 厂商 `getCpuNumByService:1007-1075`：读 Deployment 的第一个 label entry 当选择器 →
     * 列 pod 取第一个 → 读它的 `status.hostIP` → 在节点列表里找 InternalIP 相同的节点取
     * `allocatable.cpu`；任何一步失败都只记日志并用 **1**。
     */
    private Integer cpuNumByService(String name, String namespace) {
        Integer cpuNum = 1;
        String label = "";
        try {
            label = firstLabelSelector(namespace, name);
        } catch (RuntimeException e) {
            // 厂商 readNamespacedDeployment 的 ApiException 只记日志（选择器留空 ⇒ 列全部 pod）。
            log.error("获取服务的deployment:{}失败,异常为:{}", name, e.getMessage());
        }
        String pods = "";
        List<String> podNames;
        try {
            podNames = podNamesByLabel(namespace, label);
        } catch (RuntimeException e) {
            log.error("获取容器异常为:{}", e.getMessage());
            podNames = new ArrayList<>();
        }
        if (!podNames.isEmpty()) {
            pods = podNames.get(0);
        }
        String hostIp = "";
        try {
            String ip = serviceRuntimePort.readPodHostIp(namespace, pods);
            if (ip != null) {
                hostIp = ip;
            }
        } catch (RuntimeException e) {
            log.error("获取容器:{}所属节点ip失败,默认设置为1核,异常为:{}", pods, e.getMessage());
        }
        try {
            java.math.BigDecimal cpu = serviceRuntimePort.readNodeAllocatableCpuByInternalIp(hostIp);
            if (cpu != null) {
                cpuNum = cpu.intValue();
            }
        } catch (RuntimeException e) {
            log.error("获取节点信息失败,默认设置为1核,异常为:{}", e.getMessage());
        }
        log.info("获取到容器:{}所属节点的cpu的核数为:{}", pods, cpuNum);
        return cpuNum;
    }

    /** 厂商 `getServiceCpuMonitor:1090-1099`（`cpuNum` 参数厂商收了却**从不使用**，这里也不传）。 */
    private List<ServiceMonitorSeriesVO> serviceCpuMonitor(String name, long start, long end, List<String> podNames) {
        int step = stepBySecond(start, end);
        String podNameExp = name + "-.*";
        if (!podNames.isEmpty()) {
            podNameExp = String.join("|", podNames);
        }
        // 照抄：`))  by (pod) ` 是双空格 + 行尾空格。
        String query = "sum(rate(container_cpu_usage_seconds_total{pod_name=~\"" + podNameExp
                + "\",container_name!=\"\",container_name!=\"POD\"}[5m]))  by (pod) ";
        return toServiceSeriesFromMetricPod(rangeQuery(query, start, end, step));
    }

    /** 厂商 `getServiceMemoryMonitor:1101-1109`。 */
    private List<ServiceMonitorSeriesVO> serviceMemoryMonitor(String name, long start, long end,
                                                              List<String> podNames) {
        int step = stepBySecond(start, end);
        String podNameExp = name + "-.*";
        if (!podNames.isEmpty()) {
            podNameExp = String.join("|", podNames);
        }
        String query = "container_memory_usage_bytes{pod=~\"" + podNameExp
                + "\",container_name!=\"\",container_name!=\"POD\"}";
        return toServiceSeriesFromMetricPod(rangeQuery(query, start, end, step));
    }

    /** 厂商 `getServiceOutboundNetworkMonitor:1111-1122`（transmit）。 */
    private List<ServiceMonitorSeriesVO> serviceOutboundNetworkMonitor(String name, String podName,
                                                                       long start, long end,
                                                                       List<String> podNames) {
        int step = stepBySecond(start, end);
        String query;
        if (!podNames.isEmpty()) {
            query = "rate(container_network_transmit_bytes_total{pod_name=~\"" + String.join("|", podNames)
                    + "\"}[2m])";
        } else {
            query = "rate(container_network_transmit_bytes_total{pod_name=~\"^" + name + ".*$\"}[2m])";
        }
        return toServiceSeriesWithPodName(rangeQuery(query, start, end, step), podName);
    }

    /** 厂商 `getServiceDiskReadIoMonitor:1137-1148`。 */
    private List<ServiceMonitorSeriesVO> serviceDiskReadIoMonitor(String name, long start, long end,
                                                                  List<String> podNames) {
        int step = stepBySecond(start, end);
        String query;
        if (!podNames.isEmpty()) {
            query = "irate(container_fs_reads_bytes_total{pod=~\"" + String.join("|", podNames) + "\"}[2m])";
        } else {
            query = "irate(container_fs_reads_bytes_total{pod=~\"" + name + ".*\"}[2m])";
        }
        return toServiceSeriesFromMetricPod(rangeQuery(query, start, end, step));
    }

    /** 厂商 `getServiceDiskWriteIoMonitor:1150-1161`。 */
    private List<ServiceMonitorSeriesVO> serviceDiskWriteIoMonitor(String name, long start, long end,
                                                                   List<String> podNames) {
        int step = stepBySecond(start, end);
        String query;
        if (!podNames.isEmpty()) {
            query = "irate(container_fs_writes_bytes_total{pod=~\"" + String.join("|", podNames) + "\"}[2m])";
        } else {
            query = "irate(container_fs_writes_bytes_total{pod=~\"" + name + ".*\"}[2m])";
        }
        return toServiceSeriesFromMetricPod(rangeQuery(query, start, end, step));
    }

    /**
     * 厂商 `getOutBoundNetWorkMonitor:1176-1216`：服务名长度 &gt; 8 时截成前 7 个字符；
     * `podName` 非空只查一个 pod，否则按 Deployment 第一个 label 选择器**逐个 pod** 查再合并。
     */
    private List<ServiceMonitorSeriesVO> outBoundNetWorkMonitor(String name, String namespace, String podName,
                                                                long start, long end, List<String> podNames) {
        String substring = name;
        if (name.length() > 8) {
            substring = name.substring(0, 7);
        }
        List<ServiceMonitorSeriesVO> serviceMonitorList = new ArrayList<>();
        if (!isBlank(podName)) {
            return serviceOutboundNetworkMonitor(substring, podName, start, end, podNames);
        }
        String label = firstLabelSelector(namespace, name);
        log.info("获取到label标签为:{}", label);
        for (String pod : podNamesByLabel(namespace, label)) {
            serviceMonitorList.addAll(serviceOutboundNetworkMonitor(substring, pod, start, end, podNames));
        }
        return serviceMonitorList;
    }

    /**
     * 厂商 `getInBoundNetWorkMonitor:1218-1258`。
     *
     * <p><b>照抄的厂商缺陷</b>：两个分支都调用的是 {@code getServiceOutboundNetworkMonitor}
     * （transmit），所以 `inboundNetwork` 拿到的其实是**出站**数据。这里刻意保持同一行为。
     */
    private List<ServiceMonitorSeriesVO> inBoundNetWorkMonitor(String name, String namespace, String podName,
                                                               long start, long end, List<String> podNames) {
        String substring = name;
        if (name.length() > 8) {
            substring = name.substring(0, 7);
        }
        List<ServiceMonitorSeriesVO> serviceMonitorList = new ArrayList<>();
        if (!isBlank(podName)) {
            return serviceOutboundNetworkMonitor(substring, podName, start, end, podNames);
        }
        String label = firstLabelSelector(namespace, name);
        log.info("获取到label标签为:{}", label);
        for (String pod : podNamesByLabel(namespace, label)) {
            serviceMonitorList.addAll(serviceOutboundNetworkMonitor(substring, pod, start, end, podNames));
        }
        return serviceMonitorList;
    }

    /**
     * 厂商 `getServiceMonitorInfo:909-955`：`podName` 取 `metric.pod`，
     * `value`/`floatValue` **都是原始串**；缺 `metric.pod` 时厂商 `.toString()` NPE、被 catch-all 吞掉
     * ⇒ **整条响应退化成空列表**（照抄）。
     */
    private List<ServiceMonitorSeriesVO> toServiceSeriesFromMetricPod(
            List<IPrometheusMetricPort.RangeSeries> seriesList) {
        List<ServiceMonitorSeriesVO> out = new ArrayList<>();
        try {
            for (IPrometheusMetricPort.RangeSeries series : seriesList) {
                String podName = series.metric().get("pod");
                if (podName == null) {
                    throw new NullPointerException("metric.pod 缺失（厂商此处 .toString() 会 NPE）");
                }
                ServiceMonitorSeriesVO serviceMonitor = new ServiceMonitorSeriesVO();
                serviceMonitor.setPodName(podName);
                serviceMonitor.setMetrics(servicePoints(series));
                out.add(serviceMonitor);
            }
        } catch (Exception e) {
            log.error("请求获取service信息接口出错:", e);
            return new ArrayList<>();
        }
        return out;
    }

    /**
     * 厂商 `getNetWorkMonitorInfo:872-907`：`podName` 取**入参**，而 `metric.pod` 被读进一个
     * **从不使用**的局部变量（缺键同样 NPE ⇒ 整条响应空列表，照抄）。
     */
    private List<ServiceMonitorSeriesVO> toServiceSeriesWithPodName(
            List<IPrometheusMetricPort.RangeSeries> seriesList, String podName) {
        List<ServiceMonitorSeriesVO> out = new ArrayList<>();
        try {
            for (IPrometheusMetricPort.RangeSeries series : seriesList) {
                if (series.metric().get("pod") == null) {
                    throw new NullPointerException("metric.pod 缺失（厂商此处 .toString() 会 NPE）");
                }
                ServiceMonitorSeriesVO serviceMonitor = new ServiceMonitorSeriesVO();
                serviceMonitor.setPodName(podName);
                serviceMonitor.setMetrics(servicePoints(series));
                out.add(serviceMonitor);
            }
        } catch (Exception e) {
            log.error("请求获取service信息接口出错:", e);
            return new ArrayList<>();
        }
        return out;
    }

    private static List<MonitorSeriesVO.Point> servicePoints(IPrometheusMetricPort.RangeSeries series) {
        List<MonitorSeriesVO.Point> points = new ArrayList<>();
        for (IPrometheusMetricPort.RangeSample sample : series.values()) {
            MonitorSeriesVO.Point point = new MonitorSeriesVO.Point();
            point.setTimestamp(formatInstant(Instant.ofEpochSecond(sample.timestamp())));
            point.setFloatValue(sample.value());
            point.setValue(sample.value());
            points.add(point);
        }
        return points;
    }

    /** 厂商那三处"读 Deployment 拿第一个 label entry"的公共实现。 */
    private String firstLabelSelector(String namespace, String serviceName) {
        Map<String, String> labels = serviceRuntimePort.readDeploymentLabels(namespace, serviceName);
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        Map.Entry<String, String> entry = labels.entrySet().iterator().next();
        return entry.getKey() + "=" + entry.getValue();
    }

    /** `labelSelector` 为空串时列**该命名空间全部 pod**（厂商传空串给 listNamespacedPod 的效果）。 */
    private List<String> podNamesByLabel(String namespace, String labelSelector) {
        List<String> names = new ArrayList<>();
        if (isBlank(labelSelector)) {
            for (Map<String, Object> pod : serviceRuntimePort.listAllPods(namespace)) {
                Map<String, Object> metadata = asMap(pod.get("metadata"));
                if (metadata != null && metadata.get("name") != null) {
                    names.add(String.valueOf(metadata.get("name")));
                }
            }
            return names;
        }
        int index = labelSelector.indexOf('=');
        String key = index < 0 ? labelSelector : labelSelector.substring(0, index);
        String value = index < 0 ? "" : labelSelector.substring(index + 1);
        for (com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO pod
                : serviceRuntimePort.listPodsByLabel(namespace, key, value)) {
            names.add(pod.getName());
        }
        return names;
    }

    // ---------------------------------------------------------------- 节点利用率（厂商 :1441-1552）

    /**
     * 厂商 `getMemoryUsage:1511-1526`：失败 ⇒ `SystemException`（HTTP 500），
     * **不是**像磁盘那样落 0.0。
     */
    private List<NodeUsageVO.Item> memoryUsage() {
        try {
            String query = "(node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes)>0";
            log.debug("请求普罗米修斯：url = {}", query);
            return toNodeList(prometheusMetricPort.queryVector(query));
        } catch (Exception e) {
            log.error("获取内存状态失败", e);
            throw new SystemException("k8s.get.node.resource.availability.memory.status");
        }
    }

    /** 厂商 `getCpuUsage:1528-1537`：**没有 try**，查询失败直接冒出去（⇒ 500）。 */
    private List<NodeUsageVO.Item> cpuUsage() {
        String query = "(1-avg(irate(node_cpu_seconds_total{mode=\"idle\"}[1h])) by (node))*100";
        log.debug("请求普罗米修斯：url = {}", query);
        return toNodeList(prometheusMetricPort.queryVector(query));
    }

    /** 厂商 `getNodeList:1539-1552`：`name = metric.node`、`data = Double.parseDouble(value[1])`。 */
    private static List<NodeUsageVO.Item> toNodeList(List<IPrometheusMetricPort.VectorSample> samples) {
        List<NodeUsageVO.Item> usage = new ArrayList<>();
        for (IPrometheusMetricPort.VectorSample sample : samples) {
            String node = sample.metric().get("node");
            if (node == null) {
                throw new NullPointerException("metric.node 缺失（厂商此处 .toString() 会 NPE）");
            }
            usage.add(NodeUsageVO.Item.builder()
                    .name(node)
                    .data(Double.parseDouble(sample.value()))
                    .alert(false)
                    .build());
        }
        return usage;
    }

    /** 厂商 `getHostDetail:717-720`：读节点的 `status.allocatable["cpu"]`（**没有**就返回 null，调用处会 NPE）。 */
    private Integer allocatableCpuOf(String nodeName) {
        for (NodeSummaryVO node : nodeRepository.listNodes()) {
            if (nodeName != null && nodeName.equals(node.getName())) {
                return node.getCpuAllocatable();
            }
        }
        // 厂商 readNode 对不存在的节点抛 ApiException ⇒ 外层 catch ⇒ 400。
        throw new IllegalStateException("节点不存在: " + nodeName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    // ---------------------------------------------------------------- JSON（Jackson 被禁的等价实现）

    /** 等价于 Hutool `JSONUtil.toJsonStr(NodeAlertSetting)`：字段序即声明序。 */
    static String toJson(NodeAlertSettingVO vo) {
        if (vo == null) {
            return "null";
        }
        return "{\"cpuUsageAlert\":" + vo.getCpuUsageAlert()
                + ",\"memoryUsageAlert\":" + vo.getMemoryUsageAlert()
                + ",\"diskUsageAlert\":" + vo.getDiskUsageAlert() + "}";
    }

    /** 等价于 Jackson `readValue(val, NodeAlertSetting.class)`；解析失败时保留入参默认值。 */
    static NodeAlertSettingVO fromJson(String json, NodeAlertSettingVO fallback) {
        NodeAlertSettingVO out = new NodeAlertSettingVO();
        out.setCpuUsageAlert(orElse(CPU, json, fallback.getCpuUsageAlert()));
        out.setMemoryUsageAlert(orElse(MEMORY, json, fallback.getMemoryUsageAlert()));
        out.setDiskUsageAlert(orElse(DISK, json, fallback.getDiskUsageAlert()));
        return out;
    }

    private static Double orElse(Pattern p, String json, Double dflt) {
        Matcher m = p.matcher(json);
        return m.find() ? Double.valueOf(m.group(1)) : dflt;
    }
}
