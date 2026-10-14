package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点资源告警阈值；照抄厂商 `controller/response/tenx/node/NodeAlertSetting.java:6-9`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeAlertSettingVO {

    private Double cpuUsageAlert;
    private Double memoryUsageAlert;
    private Double diskUsageAlert;
}
