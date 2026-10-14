package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点资源告警阈值。
 * <p>照抄厂商 {@code controller/response/tenx/node/NodeAlertSetting.java:6-9}
 * （三个 {@code Double}，默认构造在 service 里是 {@code (80.0, 80.0, 80.0)}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeAlertSettingDTO {

    private Double cpuUsageAlert;
    private Double memoryUsageAlert;
    private Double diskUsageAlert;
}
