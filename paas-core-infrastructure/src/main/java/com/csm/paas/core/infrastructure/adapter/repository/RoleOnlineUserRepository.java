package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.model.valobj.OnlineUserVO;
import com.csm.paas.core.infrastructure.redis.RedisSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * "谁在线"——读 Redis 的会话列表键（**不是数据库**）。
 * <p>厂商出处：{@code RoleService.java:207-224}：
 * {@code redisService.getLoginUser("login-list")} 扫描 `login-list*`，再对每个键
 * {@code split(":")} 取 {@code [1]} 作用户名、{@code [2]} 作战位 key。
 * <p>与厂商的一处实现差异（**语义等价**）：厂商用 `SCAN` 游标遍历，这里用 `KEYS` 模式匹配。
 * 本平台规模（会话数 &lt; 100）下结果相同；会话量级上去后要换回 SCAN。
 */
@Repository
@RequiredArgsConstructor
public class RoleOnlineUserRepository implements IRoleOnlineUserRepository {

    private static final String LOGIN_LIST_PREFIX = "login-list";
    private static final String GATEWAY_TOKEN_PREFIX = "gateway-token";

    private final RedisSessionService redisSessionService;

    @Override
    public List<OnlineUserVO> queryOnlineUsers() {
        List<OnlineUserVO> online = new ArrayList<>();
        for (String key : redisSessionService.scan(LOGIN_LIST_PREFIX + "*")) {
            String[] parts = key.split(":");
            // 厂商同样只认这三段；段数不足的键直接跳过（不抛异常）
            if (parts.length < 3) {
                continue;
            }
            online.add(OnlineUserVO.builder().username(parts[1]).roleKey(parts[2]).build());
        }
        return online;
    }

    @Override
    public void evictSessionIfOnline(String username, String roleKey) {
        // 厂商 `getUserLoginStatusList` 的在线判据：login-list 里存在 split(":")[1] == username 的键
        boolean online = false;
        for (String key : redisSessionService.scan(LOGIN_LIST_PREFIX + "*")) {
            String[] parts = key.split(":");
            if (parts.length >= 2 && username.equals(parts[1])) {
                online = true;
                break;
            }
        }
        if (!online) {
            return;
        }
        String userInfo = username + ":" + roleKey;
        for (String tokenKey : redisSessionService.scan(GATEWAY_TOKEN_PREFIX + "*")) {
            if (userInfo.equals(redisSessionService.get(tokenKey))) {
                redisSessionService.delete(tokenKey);
                break;
            }
        }
        redisSessionService.delete(LOGIN_LIST_PREFIX + ":" + userInfo);
    }
}
