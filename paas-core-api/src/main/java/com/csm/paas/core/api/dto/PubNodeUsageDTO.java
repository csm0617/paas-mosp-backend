package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/pub/nodeUsage` 的响应体（厂商 `controller/response/tenx/node/NodeUsageInfo`：
 * `{cpuUsage, memoryUsage, diskUsage}`）。
 *
 * <p>厂商出处：`PublicComputerController.nodeUsage:105-111` → `ComputerService.getNodeUsage:1441-1509`。
 *
 * <p><b>类 C：本环境不可验证</b>——依赖 Prometheus 即时查询（本机不可达）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeUsageDTO {

    private List<Item> cpuUsage;

    private List<Item> memoryUsage;

    private List<Item> diskUsage;

    /** 厂商 `controller/response/tenx/node/Node`：`{name, data, alert}`（`alert` 默认 false）。 */
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
