package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.sync.adapter.port.ISyncPeerPort;
import com.csm.paas.core.domain.sync.model.valobj.SyncFetchResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 对端 GAOP 节点 HTTP 端口实现（厂商 `SyncController` 里的 `RestTemplate`）。
 *
 * <p>本仓 infrastructure 的既有做法是用 JDK `java.net.http.HttpClient`
 * （见 `DockerRegistryPort` / `NodeRepository`），这里沿用；JSON 用 jackson-databind 解析，
 * 域层只拿到 `data` 节点的原样对象（ArrayList / LinkedHashMap）。
 *
 * <p><b>类 C：本环境不可验证</b>——没有第二个 GAOP 节点可对拉。
 */
@Slf4j
@Component
public class SyncPeerPort implements ISyncPeerPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public SyncFetchResultVO getData(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("请求同步地址失败（HTTP {}）: {}", response.statusCode(), url);
                return SyncFetchResultVO.failed();
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode data = root == null ? null : root.get("data");
            Object value = (data == null || data.isNull()) ? null : MAPPER.convertValue(data, Object.class);
            return SyncFetchResultVO.of(value);
        } catch (Exception e) {
            log.error("请求同步地址失败: {}", url, e);
            return SyncFetchResultVO.failed();
        }
    }

    @Override
    public byte[] downloadPost(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Accept", "application/octet-stream, */*")
                    .timeout(Duration.ofSeconds(300))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("下载同步数据库快照失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean getBooleanData(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return false;
            }
            JsonNode data = MAPPER.readTree(response.body()).get("data");
            return data != null && data.isBoolean() && data.asBoolean();
        } catch (Exception e) {
            // 厂商 :1356-1359：任何异常 ⇒ false
            log.error("请求不到同步url数据库快照状态接口，异常:", e);
            return false;
        }
    }
}
