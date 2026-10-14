package com.csm.paas.core.trigger.websocket;

import com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 往 `/topic/software` 推"关联变化"（厂商 `updateSoftwareRelateRole` / `relateServices` 等处的
 * `convertAndSend("/topic/software", softwareStatus)`）。
 *
 * <p>载荷形状照抄厂商的 `SoftwareUpdate`：`{event, roleList}`——`roleList` 是**字符串**（单个 roleId），
 * 尽管字段名写着 "list"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareRelationBroadcaster implements ISoftwareRelationBroadcastPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void softwareRelationDeleted(String roleId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "DELETE");
            payload.put("roleList", roleId);
            messagingTemplate.convertAndSend("/topic/software", MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            // 厂商：推送失败不影响主流程
            log.warn("推送 /topic/software 失败（不影响响应）: {}", e.toString());
        }
    }
}
