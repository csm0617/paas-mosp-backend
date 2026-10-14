package com.csm.paas.core.trigger.websocket;

import com.csm.paas.core.domain.user.adapter.port.IUserInfoBroadcastPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * `IUserInfoBroadcastPort` 的 trigger 实现。
 *
 * <p>厂商 `WebSocket.sendUserInfo(text)` → `sendInfo(text, "userInfo")`：把 JSON 推到 **`userInfo`** 通道。
 * 本项目的 broker 前缀是 `/topic`（见 `StompWebSocketConfig`），所以目的地是 `/topic/userInfo`。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInfoBroadcaster implements IUserInfoBroadcastPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcastUserInfo(java.util.Map<String, Object> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            log.info("广播 userInfo: {}", json);
            messagingTemplate.convertAndSend("/topic/userInfo", json);
        } catch (Exception e) {
            log.error("序列化 userInfo 失败: {}", e.getMessage());
        }
    }
}
