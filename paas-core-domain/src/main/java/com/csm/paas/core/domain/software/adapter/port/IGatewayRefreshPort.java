package com.csm.paas.core.domain.software.adapter.port;

/**
 * 通知网关刷新规则（厂商 `SoftwareService.notificationGateway:2287-2294`）。
 *
 * <p>厂商原样：`new Thread(() -> new RestTemplate().getForObject("http://<host>:<port>/refresh", String.class)).start()`
 * ——**异步、fire-and-forget、失败完全不影响响应**（异常只落在那个线程里）。
 * 本端口把"异步 + 吞异常"的实现细节放在 infrastructure，domain 只表达"通知一下"。
 */
public interface IGatewayRefreshPort {

    /** 通知网关刷新；**永不抛异常**（照抄厂商的 fire-and-forget 语义）。 */
    void refresh();
}
