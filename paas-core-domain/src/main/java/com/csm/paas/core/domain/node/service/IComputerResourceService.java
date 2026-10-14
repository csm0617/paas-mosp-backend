package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.node.model.valobj.NodeAlertSettingVO;
import com.csm.paas.core.domain.node.model.valobj.NodeDiskVO;

import java.util.List;

/**
 * 公共 API - 计算机资源（厂商 `controller/pub/PublicComputerController.java` 里
 * `/nodeDisk`、`/getNodeAlertSetting`、`/setNodeAlertSetting` 三条）。
 *
 * <p>厂商实现：`service/ComputerService.java`
 * {@code getNodeDisk:2106-2155}、{@code setNodeAlertSetting:2157-2160}、{@code getNodeAlertSetting:2162-2180}。
 *
 * <p><b>类 C</b>：{@code nodeDisk} 依赖 Prometheus（本环境不可达，代码齐全不验证）。
 */
public interface IComputerResourceService {

    /** 所有节点的磁盘总量（Prometheus `node_filesystem_size_bytes` 求和）。 */
    List<NodeDiskVO> getNodeDisk();

    /** 写 `system.node.alert.setting`（默认 `{80,80,80}`）。 */
    void setNodeAlertSetting(NodeAlertSettingVO nodeAlertSetting);

    /** 读 `system.node.alert.setting`；键不存在时**先落库默认值再读回**（厂商 `:2166-2168`）。 */
    NodeAlertSettingVO getNodeAlertSetting();
}
