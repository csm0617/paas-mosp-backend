package com.csm.paas.core.domain.licence;

import com.csm.paas.core.domain.licence.service.impl.LicenceCommandServiceImpl;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * `GET /v1/licence/loginOutAllUser`（厂商 `LicenceService.loginOutAllUser:1146-1149` =
 * `RedisService.deleteAllRedisKey:53-57`）。规格见 `docs/findings/150`。
 */
class LicenceLoginOutAllTest {

    private final IUserSessionRepository userSessionRepository = mock(IUserSessionRepository.class);

    @Test
    void clearsAllSessionsAndReturnsTrue() {
        assertTrue(new LicenceCommandServiceImpl(userSessionRepository).loginOutAllUsers());
        verify(userSessionRepository).deleteAllSessions();
    }
}
