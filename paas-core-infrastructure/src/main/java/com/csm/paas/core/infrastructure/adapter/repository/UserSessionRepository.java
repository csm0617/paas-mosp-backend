package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.infrastructure.redis.RedisSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 会话仓储（Redis）。键/值格式与厂商一致，见 {@link IUserSessionRepository} 的说明。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSessionRepository implements IUserSessionRepository {

    private static final String TOKEN_PREFIX = "gateway-token";
    private static final String LOGIN_LIST_PREFIX = "login-list";

    private final RedisSessionService redisSessionService;

    @Override
    public void save(String token, SessionVO session, long ttlSeconds, long maxLogin) {
        String sessionKey = tokenKey(token);
        String sessionValue = sessionValue(session);
        String listKey = listKey(session);
        try {
            redisSessionService.setWithTtl(sessionKey, sessionValue, ttlSeconds);
            redisSessionService.rightPush(listKey, sessionKey);
            if (maxLogin > 0) {
                long size = redisSessionService.listSize(listKey);
                for (long i = 0; i < size - maxLogin; i++) {
                    String evicted = redisSessionService.leftPop(listKey);
                    if (evicted != null) {
                        redisSessionService.delete(evicted);
                    }
                }
            }
            redisSessionService.expire(listKey, ttlSeconds);
        } catch (Exception e) {
            // 厂商同样吞掉 Redis 失败（UserController.java:262-264），登录本身仍成功
            log.error("保存登录状态失败，用户：{}", session.getUsername(), e);
        }
    }

    @Override
    public Optional<SessionVO> find(String token) {
        String value = redisSessionService.get(tokenKey(token));
        if (value == null) {
            return Optional.empty();
        }
        String[] split = value.split(":");
        if (split.length < 2) {
            return Optional.empty();
        }
        return Optional.of(SessionVO.builder().username(split[0]).roleKey(split[1]).build());
    }

    @Override
    public void delete(String token, SessionVO session) {
        redisSessionService.delete(tokenKey(token));
        redisSessionService.removeFromList(listKey(session), tokenKey(token));
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + ":" + token;
    }

    private String listKey(SessionVO session) {
        return LOGIN_LIST_PREFIX + ":" + sessionValue(session);
    }

    private String sessionValue(SessionVO session) {
        return session.getUsername() + ":" + session.getRoleKey();
    }
}
