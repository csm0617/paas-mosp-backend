package com.csm.paas.core.domain.software.adapter.port;

/**
 * 查询 Loki 历史日志（厂商 `TenxCloudImpl.getHistoryLog:159-235` 的 HTTP 部分）。
 *
 * <p>厂商原样：
 * <pre>
 * GET &lt;prometheus.log-monitoring&gt;/loki/api/v1/query_range
 *     ?direction=FORWARD&amp;limit=&lt;size&gt;
 *     &amp;query={pod=~`&lt;serviceName&gt;.*`} | json | line_format "{{.log}}" |="&lt;keyword&gt;"
 *     &amp;start=&lt;epochNanos&gt;&amp;end=&lt;epochNanos&gt;
 * </pre>
 * 其中 `start`/`end` 是 **毫秒 × 1_000_000** 得到的纳秒。返回 Loki 的**原始 JSON 文本**，
 * 由 trigger 层解析成厂商的 `HistoryLog` 形状（domain 不引 Jackson）。
 *
 * <p>连接失败 ⇒ 厂商抛 `SystemException("can not connect to log monitor.")`（HTTP 500）。
 * <p><b>类 C：本环境不可验证</b>（本机没有 Loki；厂商默认地址 `http://localhost:30008`）。
 */
public interface ILokiLogPort {

    /**
     * 查一段历史日志。
     *
     * @param serviceName 服务名（用作 `pod=~` 前缀）
     * @param keyword     行过滤关键字（厂商默认空串）
     * @param size        `limit`
     * @param startMillis 起始时间（毫秒）
     * @param endMillis   结束时间（毫秒）
     * @return Loki 原始 JSON 文本；**非 200 响应返回 null**（厂商据此走 `Response.ok(null)`）
     * @throws com.csm.paas.core.types.exception.SystemException 连不上日志服务
     */
    String queryRange(String serviceName, String keyword, int size, long startMillis, long endMillis);
}
