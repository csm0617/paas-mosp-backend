package com.csm.paas.core.trigger.websocket;

import com.csm.paas.core.domain.cluster.adapter.port.IK8sEventPort;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 把 K8s watch 事件推到 `/topic/k8s/proxy/&lt;Kind&gt;`。
 * <p>topic 名与前端的订阅**逐字一致**：`/topic/k8s/proxy/Service`（服务发现）、`/Deployment`（部署文件）、`/Pod`（容器）。
 * <p>依赖方向：trigger → domain 端口（{@link IK8sEventPort}）；实现由 infrastructure 提供。
 */
@Slf4j
@Component
public class K8sTopicBroadcaster {

    private static final String TOPIC_PREFIX = "/topic/k8s/proxy/";

    @Resource
    private IK8sEventPort k8sEventPort;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /**
     * 应用就绪后再起 watch，并且**失败不阻断启动**——没有集群在场时（例如离线契约测试）也能起来。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            k8sEventPort.startWatch(namespace, (kind, json) ->
                    messagingTemplate.convertAndSend(TOPIC_PREFIX + kind, json));
            log.info("K8s 事件广播已就绪，topic 前缀 {}", TOPIC_PREFIX);
        } catch (Exception e) {
            log.warn("启动 K8s watch 失败（不影响其它端点）: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        k8sEventPort.stopWatch();
    }
}
