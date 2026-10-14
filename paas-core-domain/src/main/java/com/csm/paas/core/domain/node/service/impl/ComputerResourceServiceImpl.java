package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.node.adapter.repository.INodeRepository;
import com.csm.paas.core.domain.node.model.valobj.NodeAlertSettingVO;
import com.csm.paas.core.domain.node.model.valobj.NodeDiskVO;
import com.csm.paas.core.domain.node.model.valobj.NodeSummaryVO;
import com.csm.paas.core.domain.node.service.IComputerResourceService;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计算机资源实现（厂商 `service/ComputerService.java:2106-2180`）。
 *
 * <p><b>类 C</b>：`nodeDisk` 要打 Prometheus，本环境不可达；代码齐全、不做在线验证。
 *
 * <p>照抄点 / 偏差：
 * <ul>
 *   <li>PromQL 与厂商**逐字相同**，且像厂商一样把带空格/引号的 query 直接拼进 URL
 *       （厂商 `:2124-2127` 不做 URL 编码）——编码由端口实现负责拼；</li>
 *   <li>取数只看 `result[0].value[1]`，四舍五入成 {@code Long}（厂商 `:2141`）；</li>
 *   <li>**没有**取到值时不设 {@code size}（保持 null），只有**抛异常**才落 {@code 0L}
 *       （厂商 `:2146-2149`）——两种情况的结果不同，照抄；</li>
 *   <li>告警设置厂商用 Hutool `JSONUtil.toJsonStr` + Jackson `ObjectMapper` 读写。
 *       域层禁用 Jackson，这里用**等价的定点文本**处理
 *       （键序 cpu/memory/disk，值用 {@code Double.toString}）。</li>
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

    private final INodeRepository nodeRepository;
    private final ISettingRepository settingRepository;

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
