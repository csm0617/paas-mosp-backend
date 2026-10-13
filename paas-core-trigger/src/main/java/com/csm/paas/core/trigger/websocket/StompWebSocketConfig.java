package com.csm.paas.core.trigger.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS 接入点。
 * <p>前端是 `new SockJS("/api/gaop" + "/ws")`（`umi.d08aa716.js`），网关把 `/api/gaop/**` 前缀剥掉后
 * 落到后端的 `/ws/**`；用 `withSockJS()` 会一并提供 `/ws/info` 与各传输路径，前端不再 404 重试。
 * <p>简单内存 broker 足够：客户端只订阅 `/topic/**`，不发送。
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
