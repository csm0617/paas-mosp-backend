package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/pub/serviceMonitor` 的响应体（厂商 `controller/response/tenx/ServiceMetric`，6 个键）。
 *
 * <p>厂商出处：`PublicComputerController.serviceMonitorDetail:88-103` →
 * `ComputerService.getServiceDetail:968-1005`。
 *
 * <p><b>照抄的怪点</b>：`inboundNetwork` 实际查的是 `container_network_transmit_bytes_total`
 * （厂商 `getInBoundNetWorkMonitor` 调错了方法）⇒ 入站曲线与出站曲线**同源**。
 *
 * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubServiceMonitorDTO {

    private List<Series> cpu;

    private List<Series> memory;

    private List<Series> outboundNetwork;

    /** 照抄：厂商这里拿的是 transmit 指标。 */
    private List<Series> inboundNetwork;

    private List<Series> diskReadIo;

    private List<Series> diskWriteIo;

    /** 厂商 `controller/response/tenx/ServiceMonitor`：`{podName, metrics}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Series {

        private String podName;

        private List<PubMonitorPointDTO> metrics;
    }
}
