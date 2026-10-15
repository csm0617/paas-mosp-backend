package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一条监控曲线（厂商 `controller/response/tenx/TenxMonitorInfo`）。
 *
 * <p>厂商出处：`ComputerService.getMonitorInfo:839-870`（节点维度）与 `ServiceController.getServiceItemInfo`
 * 消费的 `TenxMonitorInfo` 同形：`{metrics, latestTimestamp}`（字段声明顺序即 JSON 键顺序）。
 *
 * <p>照抄点（含怪点）：
 * <ul>
 *   <li>`latestTimestamp` 取**扁平化之后最后一个**采样点的 `timestamp`，不是"值最大的时间"；</li>
 *   <li>**Prometheus 返回的结果为空时**（`result` 为空数组）厂商在 `infoArrayList.get(size-1)` 上越界，
 *       异常被 catch 吞掉 ⇒ 返回的对象的 `metrics`/`latestTimestamp` **都保持 null**（不是空数组）；</li>
 *   <li>`value` 是**四舍五入后的整数字符串**（`Math.round(Double.parseDouble(raw)) + ""`），
 *       `floatValue` 才是原始浮点串——两者用途不同，照抄。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonitorSeriesVO {

    /** 采样点；**Prometheus 无数据时为 null**（厂商没有设过这个字段）。 */
    private List<Point> metrics;

    /** 最后一个采样点的时间戳（ISO-8601 offset 形式）；无数据时 null。 */
    private String latestTimestamp;

    /** 厂商 `controller/response/tenx/DataInfo`：`{timestamp, value, floatValue}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Point {

        /** `OffsetDateTime.ofInstant(Instant.ofEpochSecond(<秒>), ZoneId.systemDefault()).toString()`。 */
        private String timestamp;

        /** 四舍五入后的整数字符串（节点维度），或（服务维度）原样字符串。 */
        private String value;

        /** Prometheus 返回的原始值字符串。 */
        private String floatValue;
    }
}
