package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 服务监控（厂商 `GET /v1/pub/serviceMonitor` → `ComputerService.getServiceDetail:968-1005` 返回的
 * `controller/response/tenx/ServiceMetric`）。
 *
 * <p>字段与 key 顺序照厂商 `ServiceMetric` 声明顺序：cpu/memory/outboundNetwork/inboundNetwork/
 * diskReadIo/diskWriteIo，每一项是 `List&lt;ServiceMonitor&gt;`。
 *
 * <p><b>照抄的怪点</b>：`inboundNetwork` 那条链路厂商**调错了方法**——`getInBoundNetWorkMonitor:1218-1258`
 * 两个分支都去调 `getServiceOutboundNetworkMonitor`（transmit 指标）。也就是说
 * **入站曲线拿的是出站数据**。我们照抄（见 `ComputerResourceServiceImpl.serviceMonitor`）。
 *
 * <p><b>类 C：本环境不可验证</b>（Prometheus 不可达）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceMonitorVO {

    private List<ServiceMonitorSeriesVO> cpu;

    private List<ServiceMonitorSeriesVO> memory;

    private List<ServiceMonitorSeriesVO> outboundNetwork;

    /** 照抄：厂商这里实际查的是 `container_network_transmit_bytes_total`。 */
    private List<ServiceMonitorSeriesVO> inboundNetwork;

    private List<ServiceMonitorSeriesVO> diskReadIo;

    private List<ServiceMonitorSeriesVO> diskWriteIo;
}
