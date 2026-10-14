package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通知网关刷新规则（厂商 `SoftwareService.notificationGateway:2287-2294`）：
 * `new Thread(() -> new RestTemplate().getForObject("http://<gaop.gateway.host>:<gaop.gateway.port>/refresh", String.class)).start()`。
 *
 * <p>厂商配置默认值（`application.yml:114-116` / `application-k8s.yml:38-40`）：
 * 本地 profile `host=gateway`，k8s profile `host=gaop-gateway-auth`，`port=5000`。
 *
 * <p><b>照抄的语义</b>：异步、fire-and-forget、**失败完全不影响响应**（异常只落在那个线程里）。
 * <p><b>一处主动收紧</b>：厂商的 RestTemplate **没有超时**（网关不可达时那个线程会一直挂着）；
 * 这里给 3 秒超时。对客户端不可观测，只是不泄漏线程。
 */
@Slf4j
@Component
public class GatewayRefreshPort implements IGatewayRefreshPort {

    @Value("${gaop.gateway.host:gaop-gateway-auth}")
    private String host;

    @Value("${gaop.gateway.port:5000}")
    private int port;

    @Override
    public void refresh() {
        String url = "http://" + host + ":" + port + "/refresh";
        Thread thread = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
                log.debug("通知网关更新规则完成：{}", url);
            } catch (Throwable e) {
                // 厂商同款：异常只落在后台线程里，响应照常返回
                log.warn("通知网关更新规则失败（不影响响应）：{} - {}", url, e.toString());
            }
        }, "gaop-gateway-refresh");
        thread.setDaemon(true);
        thread.start();
    }
}
