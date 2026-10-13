package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.domain.user.service.impl.AuthServiceImpl;
import com.csm.paas.core.types.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证领域服务的契约（离线）。
 * <p>口令校验用**厂商种子里的真哈希**（`artifacts/k8s/seed/msop-seed.sql` 里 admin 的 BCrypt 摘要，明文 admin123）。
 */
class AuthServiceImplTest {

    /** 种子数据里 admin 的 BCrypt 摘要。 */
    private static final String ADMIN_HASH = "$2a$10$l1gMJx2Qyp4CnZQO5yFJ9.Lur16Bk.BIAtCL78RLh5uukrgN.UUCC";

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final IUserSessionRepository sessionRepository = mock(IUserSessionRepository.class);
    private final ISettingRepository settingRepository = mock(ISettingRepository.class);

    private AuthServiceImpl service() {
        return new AuthServiceImpl(userRepository, roleRepository, sessionRepository, settingRepository);
    }

    @Test
    void loginIssuesUuidTokenAndStoresVendorCompatibleSession() {
        when(userRepository.queryByUsername("admin")).thenReturn(Optional.of(user("admin")));
        when(roleRepository.queryByKey("admin")).thenReturn(Optional.of(RoleEntity.builder().id("r1").key("admin").build()));
        when(settingRepository.queryIntValue("system.permission.expired_time", 30)).thenReturn(1440);

        LoginResultEntity result = service().login("admin", "admin123", "admin");

        assertNotNull(result.getToken());
        assertEquals(36, result.getToken().length(), "token 应是 36 字符的 UUID（厂商同款）");
        assertEquals(UUID.fromString(result.getToken()).toString(), result.getToken());
        assertEquals(86400L, result.getTtlSeconds(), "1440 分钟 → 86400 秒");
        assertNull(result.getUser().getPassword(), "响应里口令必须为 null（厂商行为）");
        assertEquals("admin", result.getUser().getLastLoginRole());

        verify(sessionRepository).save(eq(result.getToken()),
                any(SessionVO.class), eq(86400L), eq(0L));
    }

    @Test
    void wrongPasswordIsRejectedAndNothingIsStored() {
        when(userRepository.queryByUsername("admin")).thenReturn(Optional.of(user("admin")));
        assertThrows(BusinessException.class, () -> service().login("admin", "wrong", "admin"));
        verify(sessionRepository, never()).save(any(), any(), any(Long.class).longValue(), any(Long.class).longValue());
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.queryByUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service().login("nobody", "x", "admin"));
    }

    @Test
    void unknownRoleIsRejected() {
        when(userRepository.queryByUsername("admin")).thenReturn(Optional.of(user("admin")));
        when(roleRepository.queryByKey("ghost")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service().login("admin", "admin123", "ghost"));
    }

    @Test
    void sessionLookupUsesTokenAndLogoutDeletesIt() {
        SessionVO session = SessionVO.builder().username("admin").roleKey("admin").build();
        when(sessionRepository.find("t")).thenReturn(Optional.of(session));

        assertTrue(service().currentSession("t").isPresent());
        service().logout("t");

        verify(sessionRepository).delete("t", session);
    }

    private static UserEntity user(String username) {
        return UserEntity.builder()
                .id("u1").username(username).password(ADMIN_HASH).status(1)
                .nickname("管理员").desc("平台管理员").build();
    }
}
