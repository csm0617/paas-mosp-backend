package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `controller/response/tenx/DataInfo`（监控采样点）：`{timestamp, value, floatValue}`。
 *
 * <p>用在 `/v1/pub/nodeMonitor` 与 `/v1/pub/serviceMonitor` 的每一条曲线里。
 * <ul>
 *   <li>`timestamp` = `OffsetDateTime.ofInstant(Instant.ofEpochSecond(<秒>), ZoneId.systemDefault()).toString()`；</li>
 *   <li>节点维度：`value` 是 `Math.round(原始值)` 的整数字符串、`floatValue` 是原始串；</li>
 *   <li>服务维度：`value` 与 `floatValue` **都是**原始串；</li>
 *   <li>厂商为了对齐查询起点会**插一条** `value`/`floatValue` 均为 null 的点（见
 *       `ComputerService.addStartValueToMonitorInfoDetail:781-794`）。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubMonitorPointDTO {

    private String timestamp;

    private String value;

    private String floatValue;
}
