package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 `HistoryLog`（`controller/response/HistoryLog.java`）：`{podLogs, count}`。
 *
 * <p>厂商出处：`TenxCloudImpl.getHistoryLog:159-235`。两种"空"的情形**都是这个形状**（不是 null）：
 * <ol>
 *   <li>Loki 响应没有 `status` 字段，或 `data.stats.summary.totalEntriesReturned == 0`
 *       ⇒ `{podLogs: [], count: 0}`；</li>
 *   <li>正常有数据 ⇒ 按 `pod → container` 两级聚合，`count` 是 **pod 的个数**（不是日志行数）。</li>
 * </ol>
 */
@Data
@Builder
public class HistoryLogDTO {

    private List<PodLogDTO> podLogs;
    /** pod 数量（厂商 `historyLog.setCount(podLogs.size())`）。 */
    private Integer count;

    /** 厂商 `PodLog{podName, containerLogs, count}`；`count` 是 **container 个数**。 */
    @Data
    @Builder
    public static class PodLogDTO {
        private String podName;
        private List<ContainerLogDTO> containerLogs;
        private Integer count;
    }

    /** 厂商 `ContainerLog{container, filename, logs, count}`；`count` 是 **日志行数**。 */
    @Data
    @Builder
    public static class ContainerLogDTO {
        private String container;
        private String filename;
        private List<LogInfoDTO> logs;
        private Integer count;
    }

    /** 厂商 `LogInfo{timeNano, log}`：`timeNano` 是 Loki 给的**纳秒时间戳字符串**（照抄，不转数字）。 */
    @Data
    @Builder
    public static class LogInfoDTO {
        private String timeNano;
        private String log;
    }
}
