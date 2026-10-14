package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.ILokiLogPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Loki 日志端口实现（JDK `HttpClient`，与厂商的 OkHttp 等价）。
 *
 * <p>厂商出处：`TenxCloudImpl.getHistoryLog:159-235`。URL 逐字照抄，**包括**
 * `{pod=~`...`}` 这种反引号正则、`| json | line_format "{{.log}}" |=` 这条 Loki pipeline，
 * 以及 `start = 毫秒 × 1_000_000`（纳秒）。
 *
 * <p><b>类 C：本环境不可验证</b>——本机没有 Loki。地址配置 `prometheus.log-monitoring`
 * （厂商 `application.yml:199`，默认 `http://localhost:30008`）。
 */
@Slf4j
@Component
public class LokiLogPort implements ILokiLogPort {

    @Value("${prometheus.log-monitoring:http://localhost:30008}")
    private String logMonitoring;

    /** 厂商 OkHttp 没设超时；这里给 10 秒，避免日志服务不可达时把请求线程挂死。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public String queryRange(String serviceName, String keyword, int size, long startMillis, long endMillis) {
        String start = String.valueOf(startMillis * 1_000_000L);
        String end = String.valueOf(endMillis * 1_000_000L);
        String kw = keyword == null ? "" : keyword;
        String url = logMonitoring + "/loki/api/v1/query_range?direction=FORWARD&limit=" + size
                + "&query={pod=~`" + serviceName + ".*`} | json | line_format \"{{.log}}\" |=\""
                + URLEncoder.encode(kw, StandardCharsets.UTF_8) + "\""
                + "&start=" + start + "&end=" + end;
        log.debug("loki查询历史日志：{}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 厂商 OkHttp 版不看状态码，直接解析 body；但 body 不是 JSON 时它会在 readTree 抛异常 ⇒ 500。
                // 我们返回 null，让上层走"空日志"（deviations B26，行为更稳）。
                log.warn("loki 返回非 200：{}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            // 厂商：`throw new SystemException("can not connect to log monitor.")`
            throw new com.csm.paas.core.types.exception.SystemException("can not connect to log monitor.");
        }
    }
}
