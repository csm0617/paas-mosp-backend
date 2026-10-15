package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `controller/response/tenx/TenxMonitorInfo`（节点监控的一条曲线）：`{metrics, latestTimestamp}`。
 *
 * <p>键顺序照厂商字段声明顺序（`metrics` 在前）。
 * <p>**Prometheus 查不到数据时两个键都是 null**（厂商在 `getMonitorInfo:862` 先取最后一个采样点、
 * 再 `setMetrics`，空列表直接越界被 catch 吞掉）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubMonitorInfoDTO {

    private List<PubMonitorPointDTO> metrics;

    private String latestTimestamp;
}
