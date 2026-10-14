package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IPrometheusMetricPort;
import com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Prometheus 即时查询端口实现（厂商 `SoftwareService.getResourceMetric:2023-2047` 的
 * `HttpRequest.get(url).timeout(10000)` 等价物）。
 *
 * <p>逐行照抄：URL 是 `<prometheus.system-monitoring>/api/v1/query?query=<PromQL>`，
 * 解析 `data.result[i].metric.pod_name` / `.pod_namespace` / `value[1]`，
 * **任何异常都只记日志并返回空列表**。
 *
 * <p><b>类 C：本环境不可验证</b>——本机没有 Prometheus。地址配置 `prometheus.system-monitoring`
 * （厂商 `application.yml:197-198`，默认 `http://localhost:30003`）。
 */
@Slf4j
@Component
public class PrometheusMetricPort implements IPrometheusMetricPort {

    @Value("${prometheus.system-monitoring:http://localhost:30003}")
    private String systemMonitoring;

    private static final Duration TIMEOUT = Duration.ofMillis(10000);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public List<TopPodResourceVO> query(String query) {
        List<TopPodResourceVO> result = new ArrayList<>();
        String url = systemMonitoring + "/api/v1/query?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("请求获取pod资源信息接口出错: 非 200 -> {}", response.statusCode());
                return result;
            }
            JsonNode node = MAPPER.readTree(response.body());
            JsonNode data = node.get("data");
            if (data == null) {
                return result;
            }
            for (JsonNode item : data.path("result")) {
                JsonNode metric = item.get("metric");
                JsonNode value = item.get("value");
                if (metric == null || value == null || value.size() < 2) {
                    continue;
                }
                result.add(TopPodResourceVO.builder()
                        .value(value.get(1).asText())
                        .podName(metric.path("pod_name").asText())
                        .namespace(metric.path("pod_namespace").asText())
                        .build());
            }
        } catch (Exception e) {
            // 厂商：catch (Exception e) { log.error(...); } 然后返回已收集的列表
            log.error("请求获取pod资源信息接口出错:", e);
        }
        return result;
    }
}
