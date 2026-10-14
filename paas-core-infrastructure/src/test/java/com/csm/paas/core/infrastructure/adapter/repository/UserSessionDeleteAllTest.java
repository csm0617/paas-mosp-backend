package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.infrastructure.redis.RedisSessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `deleteAllSessions` 只删两类键（厂商 `RedisService.deleteAllRedisKey:53-57`）。
 * <p>规格见 `docs/findings/150`。
 */
class UserSessionDeleteAllTest {

    private final RedisSessionService redis = mock(RedisSessionService.class);

    @Test
    void deletesOnlyTheTwoVendorPrefixes() {
        when(redis.scan("gateway-token:*")).thenReturn(List.of("gateway-token:t1", "gateway-token:t2"));
        when(redis.scan("login-list:*")).thenReturn(List.of("login-list:admin:user"));

        new UserSessionRepository(redis).deleteAllSessions();

        verify(redis).delete("gateway-token:t1");
        verify(redis).delete("gateway-token:t2");
        verify(redis).delete("login-list:admin:user");
        verify(redis, never()).scan("other:*");
    }

    @Test
    void emptyScansDoNotFail() {
        when(redis.scan(anyString())).thenReturn(List.of());
        new UserSessionRepository(redis).deleteAllSessions();
        verify(redis, never()).delete(anyString());
        verify(redis).scan("gateway-token:*");
        verify(redis).scan("login-list:*");
    }
}
