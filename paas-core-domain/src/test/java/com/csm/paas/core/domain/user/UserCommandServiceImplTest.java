package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.user.adapter.repository.IUserDefaultAvatarRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.DeleteUserOutcome;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.impl.UserCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final IUserDefaultAvatarRepository defaultAvatarRepository = mock(IUserDefaultAvatarRepository.class);

    private UserCommandServiceImpl service() {
        return new UserCommandServiceImpl(userRepository, defaultAvatarRepository);
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

    // ---------- prepareNewUser（新增用户的校验与派生）----------

    @Test
    void usernameRulesFollowVendor() {
        assertEquals("user.username.not.empty", key(() -> service().prepareNewUser(form("   ", "abc123"))));
        assertEquals("user.username.invalid.length", key(() -> service().prepareNewUser(form("a", "abc123"))));
        assertEquals("user.username.invalid.length", key(() -> service().prepareNewUser(form("a".repeat(37), "abc123"))));
    }

    /**
     * **锁住厂商的字符检查 bug**：他用的是 `matcher.matches()` + 字符类，只有"整串恰好一个黑名单字符"才命中，
     * 而长度校验已经拒掉了长度 1 ⇒ 带 `@`/`#` 的用户名实际**能通过**。照抄不修好。
     */
    @Test
    void blacklistCheckIsEffectivelyDeadForMultiCharUsernames() {
        when(userRepository.queryAll()).thenReturn(List.of());
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        UserEntity created = service().prepareNewUser(form("a@b#c", "abc123"));

        assertEquals("a@b#c", created.getUsername(), "厂商行为：多字符用户名里的特殊字符不会被拒");
    }

    @Test
    void usernameSpacesAreStrippedBeforeValidation() {
        when(userRepository.queryAll()).thenReturn(List.of());
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        assertEquals("ab", service().prepareNewUser(form(" a b ", "abc123")).getUsername());
    }

    @Test
    void passwordRulesFollowVendor() {
        assertEquals("user.password.invalid.length", key(() -> service().prepareNewUser(form("ab", "12345"))));
        assertEquals("user.password.invalid.length", key(() -> service().prepareNewUser(form("ab", "1".repeat(37)))));
        assertEquals("user.password.empty", key(() -> service().prepareNewUser(form("ab", "   "))));
    }

    @Test
    void emailAndDescLengthLimits() {
        UserFormVO tooLongEmail = form("ab", "abc123");
        tooLongEmail.setEmail("x".repeat(37));
        assertEquals("user.document.link.invalid.length", key(() -> service().prepareNewUser(tooLongEmail)));

        UserFormVO tooLongDesc = form("ab", "abc123");
        tooLongDesc.setDesc("x".repeat(256));
        assertEquals("user.description.invalid.length", key(() -> service().prepareNewUser(tooLongDesc)));
    }

    @Test
    void blankDescBecomesNullOnCreate() {
        when(userRepository.queryAll()).thenReturn(List.of());
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        assertNull(service().prepareNewUser(form("ab", "abc123")).getDesc(),
                "新增时空白 desc → null（注意修改时厂商置的是空串，两者不同）");
    }

    @Test
    void passwordIsHashedAndAvatarFallsBackToVendorDefault() {
        when(userRepository.queryAll()).thenReturn(List.of());
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        UserEntity created = service().prepareNewUser(form("ab", "abc123"));

        assertTrue(created.getPassword().startsWith("$2a$"), "口令必须 BCrypt 哈希");
        assertTrue(BCrypt.checkpw("abc123", created.getPassword()), "哈希必须能校验回原口令");
        assertEquals("AVATAR", created.getAvatar());
        assertNotNull(created.getId());
        assertEquals(36, created.getId().length(), "id 是 UUID");
    }

    /** 厂商 add 只设 createdAt、不设 updatedAt ⇒ 新建用户的 updatedAt 必须是 null（实测对拉）。 */
    @Test
    void newUserHasNullUpdatedAt() {
        when(userRepository.queryAll()).thenReturn(List.of());
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        UserEntity created = service().prepareNewUser(form("ab", "abc123"));

        assertNull(created.getUpdatedAt(), "厂商不设 updatedAt（页面靠 updatedAt || createdAt 兜底）");
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void explicitAvatarWins() {
        when(userRepository.queryAll()).thenReturn(List.of());
        UserFormVO withAvatar = form("ab", "abc123");
        withAvatar.setAvatar("MY-AVATAR");

        assertEquals("MY-AVATAR", service().prepareNewUser(withAvatar).getAvatar());
    }

    @Test
    void uuidAllocationFollowsVendorAlgorithm() {
        when(defaultAvatarRepository.queryDefaultAvatar()).thenReturn("AVATAR");

        // 库里一个用户都没有 → 厂商给 -1
        when(userRepository.queryAll()).thenReturn(List.of());
        assertEquals(-1, service().prepareNewUser(form("ab", "abc123")).getUuid());

        // 已有 uuid=0 的用户 → 下一个空位是 1
        when(userRepository.queryAll()).thenReturn(List.of(userWithUuid("u1", "bob", 0)));
        assertEquals(1, service().prepareNewUser(form("ab", "abc123")).getUuid());

        // 已有用户的 uuid 全是 null → 从 0 开始
        when(userRepository.queryAll()).thenReturn(List.of(userWithUuid("u1", "bob", null)));
        assertEquals(0, service().prepareNewUser(form("ab", "abc123")).getUuid());

        // 0、1 都占了但缺 2 → 取 2
        when(userRepository.queryAll()).thenReturn(List.of(
                userWithUuid("u0", "a0", 0), userWithUuid("u1", "a1", 1)));
        assertEquals(2, service().prepareNewUser(form("ab", "abc123")).getUuid());
    }

    @Test
    void uuidQuotaExceededAbove256UsersWithUuid() {
        List<UserEntity> many = new java.util.ArrayList<>();
        for (int i = 0; i < 257; i++) {
            many.add(userWithUuid("u" + i, "user" + i, i));
        }
        when(userRepository.queryAll()).thenReturn(many);

        assertEquals("user.data.limit.exceeded", key(() -> service().prepareNewUser(form("ab", "abc123"))));
    }

    private String key(java.util.concurrent.Callable<UserEntity> action) {
        return assertThrows(WrongArgumentException.class, action::call).getI18nKey();
    }

    private static UserFormVO form(String username, String password) {
        return UserFormVO.builder().username(username).password(password).nickname("昵称").status(1).build();
    }

    private static UserEntity userWithUuid(String id, String username, Integer uuid) {
        return UserEntity.builder().id(id).username(username).status(1).uuid(uuid).build();
    }

    // ---------- prepareUpdatedUser（修改用户）----------

    @Test
    void updateRejectsMissingUser() {
        when(userRepository.queryById("nope")).thenReturn(java.util.Optional.empty());
        assertEquals("user.not.found", keyUpdate(() -> service().prepareUpdatedUser("nope", form(null, null))));
    }

    @Test
    void blankPasswordKeepsExistingHash() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));

        UserEntity updated = service().prepareUpdatedUser("u1", form(null, null));

        assertEquals(EXISTING_HASH, updated.getPassword(), "口令为空 → 保持原哈希（不重新哈希）");
    }

    @Test
    void newPasswordIsValidatedAndHashed() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));
        assertEquals("user.change.password.invalid",
                keyUpdate(() -> service().prepareUpdatedUser("u1", form(null, "12345"))));

        UserEntity updated = service().prepareUpdatedUser("u1", form(null, "newpass123"));
        assertTrue(BCrypt.checkpw("newpass123", updated.getPassword()), "新口令必须重新哈希");
    }

    @Test
    void blankUsernameKeepsExisting() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));

        assertEquals("bob", service().prepareUpdatedUser("u1", form(null, null)).getUsername());
    }

    @Test
    void usernameTakenByAnotherIsRejectedButSelfIsFine() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));
        when(userRepository.queryByUsername("bob")).thenReturn(java.util.Optional.of(existing("u1", "bob")));
        // 改成自己现在的名字：不算重复
        assertEquals("bob", service().prepareUpdatedUser("u1", form("bob", null)).getUsername());

        when(userRepository.queryByUsername("carol")).thenReturn(java.util.Optional.of(existing("u2", "carol")));
        assertEquals("user.change.username.exist",
                keyUpdate(() -> service().prepareUpdatedUser("u1", form("carol", null))));
    }

    @Test
    void newUsernameLengthIsValidated() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));
        when(userRepository.queryByUsername("a")).thenReturn(java.util.Optional.empty());

        assertEquals("user.change.username.invalid",
                keyUpdate(() -> service().prepareUpdatedUser("u1", form("a", null))));
    }

    @Test
    void emailAndDescLimitsUseTheUpdateSpecificKeys() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));
        UserFormVO longEmail = form(null, null);
        longEmail.setEmail("x".repeat(37));
        assertEquals("user.mail.invalid", keyUpdate(() -> service().prepareUpdatedUser("u1", longEmail)));

        UserFormVO longDesc = form(null, null);
        longDesc.setDesc("x".repeat(256));
        assertEquals("user.change.desc.invalid", keyUpdate(() -> service().prepareUpdatedUser("u1", longDesc)));
    }

    /** 新增时空白 desc 置 null；**修改时置空串** —— 两处不同，这条最容易"顺手统一"掉。 */
    @Test
    void blankDescBecomesEmptyStringOnUpdate() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing("u1", "bob")));

        assertEquals("", service().prepareUpdatedUser("u1", form(null, null)).getDesc());
    }

    /** 厂商会把 email/birth/phone 强制写回原值（UserService.java:716-718）⇒ 表单传了也改不动。 */
    @Test
    void emailBirthPhoneAreForcedBackToExistingValues() {
        UserEntity existing = existing("u1", "bob");
        existing.setEmail("old@x.com");
        existing.setPhone("13900000000");
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing));

        UserFormVO form = form(null, null);
        form.setEmail("new@x.com");
        form.setPhone("13800000000");
        form.setBirth(java.util.Date.from(java.time.Instant.parse("2000-01-01T00:00:00Z")));

        UserEntity updated = service().prepareUpdatedUser("u1", form);

        assertEquals("old@x.com", updated.getEmail(), "email 改不动（厂商强制写回）");
        assertEquals("13900000000", updated.getPhone(), "phone 改不动");
        assertEquals(existing.getBirth(), updated.getBirth(), "birth 改不动");
    }

    @Test
    void blankAvatarKeepsExistingAndCreatedAtIsPreserved() {
        UserEntity existing = existing("u1", "bob");
        existing.setAvatar("OLD-AVATAR");
        existing.setCreatedAt(123L);
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(existing));

        UserEntity updated = service().prepareUpdatedUser("u1", form(null, null));
        assertEquals("OLD-AVATAR", updated.getAvatar());
        assertEquals(123L, updated.getCreatedAt(), "createdAt 必须保留原值");
        assertTrue(updated.getUpdatedAt() > 0L, "updatedAt 必须刷新");
    }

    private String keyUpdate(java.util.concurrent.Callable<UserEntity> action) {
        return assertThrows(WrongArgumentException.class, action::call).getI18nKey();
    }

    private static final String EXISTING_HASH = "$2a$10$existingHashValue";

    private static UserEntity existing(String id, String username) {
        return UserEntity.builder().id(id).username(username).password(EXISTING_HASH)
                .status(1).nickname("昵称").createdAt(1L).updatedAt(1L).build();
    }
}
