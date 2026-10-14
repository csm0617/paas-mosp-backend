package com.csm.paas.core.domain.user.adapter.port;

/**
 * 用户信息广播（厂商 `WebSocket.sendUserInfo(text)` → `sendInfo(text, "userInfo")`）。
 *
 * <p>厂商把 `UserStatus` 的 JSON 推到名为 **`userInfo`** 的通道；domain 只声明能力，
 * 具体推送由 trigger 层的实现完成（分层要求）。
 */
public interface IUserInfoBroadcastPort {

    /**
     * 推送用户信息（**传结构化载荷**，序列化由实现层负责——domain 模块没有 Jackson，
     * 本会话已多次踩到这个分层约束）。
     */
    void broadcastUserInfo(java.util.Map<String, Object> payload);
}
