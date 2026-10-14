package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserDefaultAvatarRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO;
import com.csm.paas.core.domain.user.service.impl.UserImportService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户导入（厂商 `UserService.importExcel:1045-1233` / `existUpdateUser:1235-1351`）。
 *
 * <p>规格出处：{@code docs/findings/100-k1-file-endpoints-spec.md}。这里覆盖 6 个验收用例
 * （空表 / admin 行 / 新用户 / 不覆盖 / 覆盖 / 长度越界）与"最容易写错"的几条怪行为。
 */
class UserImportServiceTest {

    private static final String DEFAULT_AVATAR = "base64-avatar";

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);
    private final IUserDefaultAvatarRepository avatarRepository = mock(IUserDefaultAvatarRepository.class);

    /** 内存里的 g_user（让"插入后再按 username 回查"这一步有真实语义）。 */
    private final List<UserEntity> gUser = new ArrayList<>();
    /** 记录写入的 (userId, roleId) 角色关系。 */
    private final List<String[]> roleRelations = new ArrayList<>();
    /** 记录写入的 (userId, subRoleId) 子角色关系。 */
    private final List<String[]> subRoleRelations = new ArrayList<>();
    /** 记录被删的角色关系（按 username / userId）。 */
    private final List<String> deletedRoleRelations = new ArrayList<>();
    private final List<String> deletedSubRoleRelations = new ArrayList<>();
    private final List<UserEntity> updated = new ArrayList<>();

    private UserImportService service() {
        when(avatarRepository.queryDefaultAvatar()).thenReturn(DEFAULT_AVATAR);
        when(userRepository.queryByUsername(anyString())).thenAnswer(inv -> {
            String username = inv.getArgument(0);
            return gUser.stream().filter(u -> username.equals(u.getUsername())).findFirst();
        });
        doAnswer(inv -> {
            gUser.add(inv.getArgument(0));
            return null;
        }).when(userRepository).insert(any());
        doAnswer(inv -> {
            updated.add(inv.getArgument(0));
            return null;
        }).when(userRepository).update(any());
        doAnswer(inv -> {
            deletedRoleRelations.add(inv.getArgument(0));
            return null;
        }).when(userRepository).deleteUserRoleRelationsByUsername(anyString());
        doAnswer(inv -> {
            deletedSubRoleRelations.add(inv.getArgument(0));
            return null;
        }).when(userRepository).deleteUserSubRoleRelationsByUserId(anyString());
        doAnswer(inv -> {
            roleRelations.add(new String[]{inv.getArgument(0), inv.getArgument(1)});
            return null;
        }).when(userRepository).insertUserRoleRelation(anyString(), anyString(), anyLong());
        doAnswer(inv -> {
            subRoleRelations.add(new String[]{inv.getArgument(1), inv.getArgument(2)});
            return null;
        }).when(userRepository).insertUserSubRoleRelation(anyString(), anyString(), anyString(), anyLong());
        when(roleRepository.queryByKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return Optional.ofNullable(key).map(k -> RoleEntity.builder().id("role-" + k).key(k).build());
        });
        when(subRoleRepository.queryByKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return Optional.ofNullable(key).map(k -> SubRoleEntity.builder().id("sub-" + k).key(k).build());
        });
        return new UserImportService(userRepository, roleRepository, subRoleRepository, avatarRepository);
    }

    private static ImportUserRowVO row(String username) {
        return ImportUserRowVO.builder()
                .username(username).nickname("昵称").desc("描述")
                .statusText("1").password("secret123").build();
    }

    /** 断言"某用户的关联 id 列表"（`String[]` 不能用 `equals` 比较，所以取出来比）。 */
    private static void assertRelations(List<String[]> actual, String userId, String... expectedIds) {
        assertEquals(List.of(expectedIds), actual.stream()
                .peek(r -> assertEquals(userId, r[0], "关联挂在了错误的 userId 上"))
                .map(r -> r[1]).toList());
    }

    private UserEntity existingUser(String id, String username) {
        UserEntity user = UserEntity.builder()
                .id(id).username(username).password("$2a$old").status(0).email("e@x.com")
                .nickname("老昵称").desc("老描述").avatar("老头像").uuid(7).createdAt(111L).updatedAt(222L)
                .build();
        gUser.add(user);
        return user;
    }

    // ---------- 验收用例 ----------

    @Test
    void emptySheetChangesNothing() {
        service().importUsers(List.of(), true);
        verifyNoInteractions(userRepository);
    }

    @Test
    void onlyAdminAndRootRowsChangeNothing() {
        service().importUsers(List.of(row("admin"), row("root")), true);
        assertTrue(gUser.isEmpty());
        assertTrue(roleRelations.isEmpty());
        verify(userRepository, never()).insert(any());
    }

    @Test
    void newUserIsInsertedWithUuidAvatarAndRoles() {
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("尼克").desc("描述").statusText("1").password("secret123")
                .roleKey("admin").subRoles(List.of("ops")).build()), false);

        assertEquals(1, gUser.size());
        UserEntity inserted = gUser.get(0);
        assertEquals("bob", inserted.getUsername());
        assertEquals(1, inserted.getStatus());
        assertEquals("尼克", inserted.getNickname());
        assertEquals("描述", inserted.getDesc());
        assertEquals(DEFAULT_AVATAR, inserted.getAvatar(), "avatar 取厂商的默认头像资源");
        assertNull(inserted.getUpdatedAt(), "厂商 insertSelective 不写 updated_at");
        assertNull(inserted.getUuid(), "厂商不给导入的用户分配 uuid");
        assertFalse(inserted.getId().isBlank());
        assertTrue(inserted.getPassword().startsWith("$2a$"), "口令要 BCrypt");
        assertRelations(roleRelations, inserted.getId(), "role-admin");
        assertRelations(subRoleRelations, inserted.getId(), "sub-ops");
    }

    @Test
    void existingUserWithoutCoverIsUntouched() {
        existingUser("u1", "bob");
        service().importUsers(List.of(row("bob")), false);
        assertTrue(updated.isEmpty());
        assertTrue(roleRelations.isEmpty());
        assertTrue(deletedRoleRelations.isEmpty());
    }

    @Test
    void existingUserWithCoverIsUpdatedAndRelationsRewritten() {
        existingUser("u1", "bob");
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("新昵称").desc("新描述").statusText("1").password("secret123")
                .roleKey("dev").subRoles(List.of("ops")).build()), true);

        assertEquals(1, updated.size());
        UserEntity u = updated.get(0);
        assertEquals("u1", u.getId());
        assertEquals("新昵称", u.getNickname());
        assertEquals("新描述", u.getDesc());
        assertTrue(u.getPassword().startsWith("$2a$"));
        assertEquals(0, u.getStatus(), "覆盖分支**不动** status（厂商只 set password/desc/nickname）");
        assertEquals("老头像", u.getAvatar(), "覆盖分支不动 avatar");
        assertEquals("e@x.com", u.getEmail(), "覆盖分支不动 email");
        assertEquals(111L, u.getCreatedAt());
        assertTrue(u.getUpdatedAt() > 0, "厂商最后会把 updated_at 写成 currentTime");
        assertEquals(List.of("bob"), deletedRoleRelations, "先按 username 删光角色关系");
        assertEquals(List.of("u1"), deletedSubRoleRelations);
        assertRelations(roleRelations, "u1", "role-dev");
        assertRelations(subRoleRelations, "u1", "sub-ops");
    }

    @Test
    void usernameLengthOutOfRangeIsRejected() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().importUsers(List.of(row("a")), false));
        assertEquals("user.invalid.username.length", e.getI18nKey());
    }

    // ---------- 最容易写错的怪行为 ----------

    @Test
    void blankRoleKeySkipsSubRolesToo() {
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("secret123")
                .roleKey("").subRoles(List.of("ops")).build()), false);

        assertEquals(1, gUser.size(), "用户本身照样插入");
        assertTrue(roleRelations.isEmpty(), "roleKey 空 ⇒ 不写角色");
        assertTrue(subRoleRelations.isEmpty(), "⚠️ 厂商的 continue 会**连子角色一起跳过**");
    }

    @Test
    void coverWithBlankRoleKeyStillWritesDefaultUserRole() {
        existingUser("u1", "bob");
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("secret123")
                .roleKey("").build()), true);

        // 覆盖分支判的是 `roleKey == null`，空串会走 else ⇒ 写 "user"
        assertRelations(roleRelations, "u1", "role-user");
    }

    @Test
    void nonAdminRoleKeyWritesUserPlusJsonListAndKeepsDuplicates() {
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("secret123")
                .roleKey("tester").userToRole(List.of("user", "dev")).build()), false);

        assertEquals(List.of("role-user", "role-user", "role-dev"),
                roleRelations.stream().map(r -> r[1]).toList(),
                "自动加 user，再按 col3 逐个插（**不去重**：col3 里再来一个 user 就插两行）");
    }

    @Test
    void missingStatusCellIsRejectedButEmptyIsNot() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder().username("bob").nickname("n").password("secret123").build()), false));
        assertEquals("user.import.status.must.not.null", e.getI18nKey());
        assertEquals("bob", e.getArgs()[0], "文案里带用户名");

        // 空串：不设状态、不报错
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("").password("secret123").build()), false);
        assertNull(gUser.get(0).getStatus());
    }

    @Test
    void illegalStatusValueIsRejected() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder()
                        .username("bob").nickname("n").statusText("2").password("secret123").build()), false));
        assertEquals("user.import.status.error", e.getI18nKey());
    }

    @Test
    void missingOrBlankPasswordIsRejectedShortPasswordSkipsRow() {
        WrongArgumentException missing = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder().username("bob").nickname("n").statusText("1").build()), false));
        assertEquals("user.password.empty", missing.getI18nKey());

        WrongArgumentException blank = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder()
                        .username("bob").nickname("n").statusText("1").password("  ").build()), false));
        assertEquals("user.password.empty", blank.getI18nKey());

        // <6 ⇒ 记日志跳过该行（不报错、不插入）
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("12345").build()), false);
        assertTrue(gUser.isEmpty());
    }

    @Test
    void missingNicknameIsRejected() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder()
                        .username("bob").statusText("1").password("secret123").build()), false));
        assertEquals("user.nickname.empty", e.getI18nKey());
    }

    @Test
    void tooLongDescriptionIsRejected() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class, () -> service().importUsers(
                List.of(ImportUserRowVO.builder()
                        .username("bob").nickname("n").statusText("1").password("secret123")
                        .desc("x".repeat(256)).build()), false));
        assertEquals("user.invalid.user.description.length", e.getI18nKey());
    }

    @Test
    void coverKeepsOldValuesWhenCellsAreMissing() {
        existingUser("u1", "bob");
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").password("secret123").roleKey(null).build()), true);

        UserEntity u = updated.get(0);
        assertEquals("老昵称", u.getNickname(), "col1 缺失 ⇒ 保持原值（厂商 equalToWhenPresent 的 present 是 != null）");
        assertEquals("老描述", u.getDesc(), "col5 缺失 ⇒ 保持原值");
        assertTrue(roleRelations.isEmpty(), "col2 缺失 ⇒ 只跳过角色");
        assertEquals(List.of("u1"), deletedSubRoleRelations, "子角色关系仍会被删（厂商顺序：删在角色之前、子角色之后）");
    }

    @Test
    void coverWithoutPasswordFallsBackToDefaultAndShortPasswordSkipsRow() {
        existingUser("u1", "bob");
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").password(null).roleKey("dev").build()), true);
        assertTrue(updated.get(0).getPassword().startsWith("$2a$"), "缺 col7 ⇒ 默认口令 123456 再哈希");
        assertTrue(org.springframework.security.crypto.bcrypt.BCrypt.checkpw("123456", updated.get(0).getPassword()));

        updated.clear();
        deletedRoleRelations.clear();
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").password("123").build()), true);
        assertTrue(updated.isEmpty(), "覆盖分支口令 <6 ⇒ **整行跳过**");
        assertTrue(deletedRoleRelations.isEmpty(), "跳过发生在删角色之前");
    }

    @Test
    void passwordThatIsAlreadyBCryptIsNotHashedAgain() {
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("$2a$10$alreadyHashedValue")
                .roleKey("admin").build()), false);
        assertEquals("$2a$10$alreadyHashedValue", gUser.get(0).getPassword());
    }

    @Test
    void unknownRoleAndSubRoleKeysAreSkipped() {
        UserImportService service = service();
        when(roleRepository.queryByKey("ghost")).thenReturn(Optional.empty());
        when(subRoleRepository.queryByKey("ghost")).thenReturn(Optional.empty());
        service.importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("secret123")
                .roleKey("tester").userToRole(List.of("ghost")).subRoles(List.of("ghost")).build()), false);

        assertEquals(List.of("role-user"), roleRelations.stream().map(r -> r[1]).toList());
        assertTrue(subRoleRelations.isEmpty());
    }

    @Test
    void coverUpdateIsAppliedBeforeALaterRowFails() {
        existingUser("u1", "bob");
        // 第二行用户名越界 ⇒ 400；第一行的覆盖更新**已经落库**（厂商没有事务，照抄这个中间态）
        assertThrows(WrongArgumentException.class, () -> service().importUsers(List.of(row("bob"), row("a")), true));

        assertEquals(1, updated.size(), "覆盖更新已生效");
        assertEquals(1, deletedRoleRelations.size());
    }

    @Test
    void statusIsTakenFromValidatedText() {
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("0").password("secret123").build()), false);
        assertEquals(0, gUser.get(0).getStatus());
    }

    @Test
    void subRoleIdsAreRandomUuids() {
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        service().importUsers(List.of(ImportUserRowVO.builder()
                .username("bob").nickname("n").statusText("1").password("secret123")
                .roleKey("admin").subRoles(List.of("ops")).build()), false);
        verify(userRepository).insertUserSubRoleRelation(idCaptor.capture(), eq(gUser.get(0).getId()),
                eq("sub-ops"), anyLong());
        assertEquals(36, idCaptor.getValue().length(), "id 用随机 UUID（厂商 IdUtil.randomUUID()）");
    }
}
