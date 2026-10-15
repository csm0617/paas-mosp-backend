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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus 端口实现（厂商 `SoftwareService.getResourceMetric:2023-2047` 与
 * `ComputerService.getMonitorInfo:839-870` 等处的 `HttpRequest.get(url).timeout(...)` 等价物）。
 *
 * <p>逐行照抄：URL 是 `<prometheus.system-monitoring>/api/v1/...?query=<PromQL>`，
 * 解析 `data.result[i]`；**任何异常都只记日志并返回空列表**（即时向量那条链路例外，见下）。
 *
 * <p>三种超时口径（照抄厂商）：
 * <ul>
 *   <li>{@link #query}：**10000ms**（厂商 `getResourceMetric:2043` 写死 `timeout(10000)`）；</li>
 *   <li>{@link #queryRange}：`prometheus.timeout`（厂商 `prometheusConfig.getTimeout()`，
 *       `application.yml:199` 默认 **30000**）；</li>
 *   <li>{@link #queryVector}：同上（`getNodeUsage`/`getMemoryUsage`/`getCpuUsage` 都用配置超时）。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>——本机没有 Prometheus。地址配置 `prometheus.system-monitoring`
 * （厂商 `application.yml:197-198`，默认 `http://localhost:30003`）。
 */
@Slf4j
@Component
public class PrometheusMetricPort implements IPrometheusMetricPort {

    @Value("${prometheus.system-monitoring:http://localhost:30003}")
    private String systemMonitoring;

    /** 厂商 `prometheus.timeout`（`application.yml:199`，默认 30000ms）——范围/即时向量查询用。 */
    @Value("${prometheus.timeout:30000}")
    private long prometheusTimeout;

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

    @Override
    public List<RangeSeries> queryRange(String query, long startEpochSecond, long endEpochSecond, int step) {
        List<RangeSeries> result = new ArrayList<>();
        String url = systemMonitoring + "/api/v1/query_range?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start=" + startEpochSecond + "&end=" + endEpochSecond + "&step=" + step;
        try {
            log.debug("请求普罗米修斯：url = {}", url);
            HttpResponse<String> response = send(url, Duration.ofMillis(prometheusTimeout));
            if (response == null) {
                return result;
            }
            JsonNode data = MAPPER.readTree(response.body()).get("data");
            if (data == null) {
                return result;
            }
            for (JsonNode item : data.path("result")) {
                Map<String, String> metric = new LinkedHashMap<>();
                JsonNode metricNode = item.get("metric");
                if (metricNode != null && metricNode.isObject()) {
                    metricNode.fields().forEachRemaining(e -> metric.put(e.getKey(), e.getValue().asText()));
                }
                List<RangeSample> values = new ArrayList<>();
                for (JsonNode pair : item.path("values")) {
                    if (pair.size() < 2) {
                        continue;
                    }
                    // 厂商：((Integer) jsonArray.get(0)).intValue()——小数时间戳会 ClassCastException，
                    // 被外层 catch-all 吞掉 ⇒ 整个响应退化成空列表。这里用"非整数就抛"复刻同一可观测行为。
                    JsonNode timestamp = pair.get(0);
                    if (!timestamp.isIntegralNumber()) {
                        throw new IllegalStateException(
                                "厂商此处对时间戳做 (Integer) 强转：小数时间戳会 ClassCastException，照抄为解析失败");
                    }
                    values.add(new RangeSample(timestamp.longValue(), pair.get(1).asText()));
                }
                result.add(new RangeSeries(metric, values));
            }
        } catch (Exception e) {
            // 厂商 getMonitorInfo/getServiceMonitorInfo/getNetWorkMonitorInfo 三个方法各有一段 catch-all
            log.error("请求获取监控信息接口出错:", e);
            return new ArrayList<>();
        }
        return result;
    }

    @Override
    public List<VectorSample> queryVector(String query) {
        String url = systemMonitoring + "/api/v1/query?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<VectorSample> result = new ArrayList<>();
        try {
            log.debug("请求普罗米修斯：url = {}", url);
            HttpResponse<String> response = send(url, Duration.ofMillis(prometheusTimeout));
            if (response == null) {
                return result;
            }
            JsonNode data = MAPPER.readTree(response.body()).get("data");
            // 厂商 getNodeList:1541-1542 直接 getJSONObject("data").getJSONArray("result")——
            // data 缺失就是 NPE（那条链路上没有 catch-all），所以这里也抛。
            if (data == null) {
                throw new IllegalStateException("Prometheus 响应缺少 data 字段（厂商此处会 NPE）");
            }
            JsonNode resultNode = data.get("result");
            if (resultNode == null) {
                throw new IllegalStateException("Prometheus 响应缺少 data.result 字段（厂商此处会 NPE）");
            }
            for (JsonNode item : resultNode) {
                Map<String, String> metric = new LinkedHashMap<>();
                JsonNode metricNode = item.get("metric");
                if (metricNode != null && metricNode.isObject()) {
                    metricNode.fields().forEachRemaining(e -> metric.put(e.getKey(), e.getValue().asText()));
                }
                JsonNode value = item.get("value");
                result.add(new VectorSample(metric, value == null || value.size() < 2 ? null : value.get(1).asText()));
            }
        } catch (Exception e) {
            // 与 queryRange 相反：厂商这条链路没有 catch-all（getCpuUsage 连 try 都没有），异常要冒出去。
            log.error("请求普罗米修斯即时查询失败:", e);
            throw new IllegalStateException("请求普罗米修斯即时查询失败: " + e.getMessage(), e);
        }
        return result;
    }

    /** 统一的 GET；非 200 时抛（厂商 Hutool 版不抛，但随后解析 body 必然失败 ⇒ 最终一样是异常）。 */
    private HttpResponse<String> send(String url, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Prometheus 返回非 200: " + response.statusCode());
        }
        return response;
    }
}
