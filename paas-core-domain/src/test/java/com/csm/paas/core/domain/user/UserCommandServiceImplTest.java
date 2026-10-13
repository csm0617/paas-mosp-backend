package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.DeleteUserOutcome;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.service.impl.UserCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除用户的权限矩阵（离线）。规则来源：厂商 `UserController.getDeleteUser`（`UserController.java:320-358`）。
 * <p>最反直觉的一条是**分支 3**：既不是 `admin` 用户、也没有 admin 战位的调用者，
 * 厂商返回 **200 但什么都不做**——不是 403。这条必须锁住，否则前端表现会与厂商不同。
 */
class UserCommandServiceImplTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);

    private UserCommandServiceImpl service() {
        return new UserCommandServiceImpl(userRepository);
    }

    @Test
    void adminUserCanDeleteOrdinaryUser() {
        when(userRepository.queryById("u1")).thenReturn(Optional.of(user("u1", "bob")));

        DeleteUserOutcome outcome = service().deleteUser("u1", caller("admin", true));

        assertEquals(DeleteUserOutcome.DELETED, outcome);
        verify(userRepository).deleteUserCascade("u1");
    }

    @Test
    void adminUserCannotDeleteHimself() {
        when(userRepository.queryById("u-admin")).thenReturn(Optional.of(user("u-admin", "admin")));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().deleteUser("u-admin", caller("admin", true)));
        assertEquals("role.can.not.delete.admin.key", e.getI18nKey());
        verify(userRepository, never()).deleteUserCascade("u-admin");
    }

    @Test
    void adminRoleCallerCannotDeleteAnotherAdminRoleUser() {
        when(userRepository.queryById("u2")).thenReturn(Optional.of(user("u2", "otheradmin")));
        when(userRepository.queryRoleKeysByUsername("otheradmin")).thenReturn(List.of("admin", "user"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().deleteUser("u2", caller("opadmin", true)));
        assertEquals("role.cannot.delete.op.admin", e.getI18nKey());
        verify(userRepository, never()).deleteUserCascade("u2");
    }

    @Test
    void adminRoleCallerCanDeleteOrdinaryUser() {
        when(userRepository.queryById("u3")).thenReturn(Optional.of(user("u3", "bob")));
        when(userRepository.queryRoleKeysByUsername("bob")).thenReturn(List.of("user"));

        assertEquals(DeleteUserOutcome.DELETED, service().deleteUser("u3", caller("opadmin", true)));
        verify(userRepository).deleteUserCascade("u3");
    }

    @Test
    void callerWithoutAdminRoleGetsFakeSuccessAndNothingIsDeleted() {
        when(userRepository.queryById("u4")).thenReturn(Optional.of(user("u4", "bob")));

        DeleteUserOutcome outcome = service().deleteUser("u4", caller("bob", false));

        assertEquals(DeleteUserOutcome.NOOP_NOT_PERMITTED, outcome, "厂商是 200 + 静默不删，不是 403");
        verify(userRepository, never()).deleteUserCascade("u4");
    }

    @Test
    void missingTargetThrowsUserNotFound() {
        when(userRepository.queryById("nope")).thenReturn(Optional.empty());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().deleteUser("nope", caller("admin", true)));
        assertEquals("user.id.not.found", e.getI18nKey(), "厂商控制器先走 getById，抛的是 user.id.not.found（实测对拉）");
    }

    private static UserCallerVO caller(String username, boolean adminRole) {
        return UserCallerVO.builder().username(username).adminRole(adminRole).build();
    }

    private static UserEntity user(String id, String username) {
        return UserEntity.builder().id(id).username(username).status(1).build();
    }
}
