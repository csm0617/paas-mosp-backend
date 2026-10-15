package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点利用率（厂商 `GET /v1/pub/nodeUsage` → `ComputerService.getNodeUsage:1441-1509` 返回的
 * `controller/response/tenx/node/NodeUsageInfo`：`{cpuUsage, memoryUsage, diskUsage}`）。
 *
 * <p>三组都是 `List&lt;Node&gt;`（`{name, data, alert}`），但**算法各不相同**（见
 * `ComputerResourceServiceImpl.nodeUsage` 的说明）。
 *
 * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeUsageVO {

    private List<Item> cpuUsage;

    private List<Item> memoryUsage;

    private List<Item> diskUsage;

    /** 厂商 `controller/response/tenx/node/Node`：`{name, data, alert}`，`alert` 默认 **false**。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {

        private String name;

        private Double data;

        private Boolean alert;
    }
}
