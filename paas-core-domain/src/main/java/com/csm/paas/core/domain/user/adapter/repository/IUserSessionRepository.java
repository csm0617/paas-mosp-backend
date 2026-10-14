package com.csm.paas.core.domain.user.adapter.repository;

import com.csm.paas.core.domain.user.model.valobj.SessionVO;

import java.util.Optional;

/**
 * 会话仓储（Redis）。键与值的格式**必须与厂商一致**，否则原样保留的 gaop-gateway-auth 认不出：
 * <ul>
 *   <li>会话键：`gateway-token:&lt;token&gt;` → 值 `&lt;username&gt;:&lt;战位key&gt;`</li>
 *   <li>会话列表：`login-list:&lt;username&gt;:&lt;战位key&gt;` → 列表元素 `gateway-token:&lt;token&gt;`</li>
 * </ul>
 * 依据：docs/findings/12-auth-contract.md。
 */
public interface IUserSessionRepository {

    void save(String token, SessionVO session, long ttlSeconds, long maxLogin);

    Optional<SessionVO> find(String token);

    void delete(String token, SessionVO session);

    /**
     * 清空**全部**会话（厂商 `RedisService.deleteAllRedisKey:53-57`：逐个删
     * `gateway-token:*` 与 `login-list:*` 两类键；**不是 FLUSHALL**）。
     * <p>用途：试用期到期时把所有在线用户踢回登录页（`GET /v1/licence/loginOutAllUser`）。
     */
    void deleteAllSessions();
}
