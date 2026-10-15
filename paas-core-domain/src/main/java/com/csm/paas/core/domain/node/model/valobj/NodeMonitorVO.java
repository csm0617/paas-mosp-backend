package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点监控（厂商 `GET /v1/pub/nodeMonitor` → `ComputerService.getHostDetail:693-752` 返回的
 * `controller/response/tenx/NodeInfo`）。
 *
 * <p>字段与 key 顺序照厂商 `NodeInfo` 声明顺序：先 cpu/memory，再网络进出、磁盘读写，
 * 最后四组 TCP 指标（`currEstab`/`tcpTimeouts`/`activeOpens`/`passiveOpens`）。
 *
 * <p><b>类 C：本环境不可验证</b>——依赖 Prometheus `api/v1/query_range`（厂商默认
 * `http://localhost:30003`），本机不可达；PromQL 与解析口径逐字照抄，未做在线实验。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeMonitorVO {

    /** `(1-avg(irate(node_cpu_seconds_total{node="<name>",mode="idle"}[1h])))`，采样点再乘节点核数。 */
    private MonitorSeriesVO cpu;

    private MonitorSeriesVO memory;

    private MonitorSeriesVO inboundNetwork;

    private MonitorSeriesVO outboundNetwork;

    private MonitorSeriesVO diskWriteIo;

    private MonitorSeriesVO diskReadIo;

    private MonitorSeriesVO currEstab;

    private MonitorSeriesVO tcpTimeouts;

    private MonitorSeriesVO activeOpens;

    private MonitorSeriesVO passiveOpens;
}
