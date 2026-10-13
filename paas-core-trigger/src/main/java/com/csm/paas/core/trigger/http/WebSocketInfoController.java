package com.csm.paas.core.trigger.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SockJS 的 `/ws/info` 握手信息。
 * <p>前端（STOMP over SockJS）会反复重试它；不实现会让页面一直报错刷屏。
 * <p><b>注意</b>：这里只是握手端点，真正的 WebSocket 升级尚未实现——前端会退回轮询/降级，
 * 业务数据不依赖推送（见工单 07 的待定项）。
 */
@RestController
public class WebSocketInfoController {

    @GetMapping("/ws/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("websocket", true);
        body.put("cookie_needed", false);
        body.put("origins", java.util.List.of("*:*"));
        body.put("entropy", ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
        return body;
    }
}
