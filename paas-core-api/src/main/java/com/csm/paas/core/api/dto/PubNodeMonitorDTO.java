package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `GET /v1/pub/nodeMonitor` 的响应体（厂商 `controller/response/tenx/NodeInfo`，10 个键）。
 *
 * <p>厂商出处：`PublicComputerController.getHostDetail:71-86` →
 * `ComputerService.getHostDetail:693-752`。
 *
 * <p><b>类 C：本环境不可验证</b>——依赖 Prometheus `api/v1/query_range`（本机不可达）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeMonitorDTO {

    private PubMonitorInfoDTO cpu;

    private PubMonitorInfoDTO memory;

    private PubMonitorInfoDTO inboundNetwork;

    private PubMonitorInfoDTO outboundNetwork;

    private PubMonitorInfoDTO diskWriteIo;

    private PubMonitorInfoDTO diskReadIo;

    private PubMonitorInfoDTO currEstab;

    private PubMonitorInfoDTO tcpTimeouts;

    private PubMonitorInfoDTO activeOpens;

    private PubMonitorInfoDTO passiveOpens;
}
