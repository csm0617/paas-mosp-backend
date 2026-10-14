package com.csm.paas.core.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Harbor 读取网关（厂商 `third/Harbor` + `config/FeignConfig.harbor()` 的等价物）。
 *
 * <p>三个与厂商**同名**的配置项（便于在真实环境里直接复用同一份配置）：
 * `server.host.harbor`（如 `192.168.1.52:30002`）、`docker.username`、`docker.password`。
 * <p>鉴权头照抄厂商：`Authorization: Basic ` + **URL-safe** base64(`user:pass`)
 * （厂商用的是 `Base64.getUrlEncoder()`，虽然不常见但能工作——不"顺手修正"）。
 * <p>分页也照抄 `allPageProjects`：`page` 从 1 开始、`page_size=100`，keyword 非空时带 `name=`，
 * 直到某一页不足 100 条为止。
 */
@Slf4j
@Component
public class HarborGateway {

    private static final int PAGE_SIZE = 100;

    private final ObjectMapper mapper = new ObjectMapper();
    /** 用 JDK 自带的 HttpClient（infrastructure 模块没有 spring-web，也不为这一个调用引依赖）。 */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${server.host.harbor:}")
    private String harborHost;

    @Value("${docker.username:}")
    private String username;

    @Value("${docker.password:}")
    private String password;

    /** 全量项目（厂商 `allPageProjects`）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAllProjects(String keyword) {
        List<Map<String, Object>> all = new ArrayList<>();
        if (harborHost == null || harborHost.isBlank()) {
            // 未配置 Harbor：按"没有数据"处理（与厂商拿不到 Harbor 时的表现一致——调用方只看到空列表）
            log.warn("未配置 server.host.harbor，Harbor 项目列表按空处理");
            return all;
        }
        for (int page = 1; ; page++) {
            String url = "http://" + harborHost + "/api/v2.0/projects?page=" + page + "&page_size=" + PAGE_SIZE
                    + ((keyword == null || keyword.isBlank()) ? "" : "&name=" + keyword);
            List<Map<String, Object>> items;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Basic " + basicAuth())
                        .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    log.error("Harbor 返回非 2xx：{} {}", response.statusCode(), url);
                    break;
                }
                items = mapper.readValue(response.body(), List.class);
            } catch (Exception e) {
                log.error("读取 Harbor 项目失败 url={}: {}", url, e.getMessage());
                break;   // 厂商那边抛异常会冒泡；这里按"到此为止"处理，已取到的部分照常返回
            }
            if (items == null || items.isEmpty()) {
                break;
            }
            all.addAll(items);
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("Harbor 读到项目 {} 个（keyword={}）", all.size(), keyword);
        return all;
    }

    private String basicAuth() {
        return Base64.getUrlEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
