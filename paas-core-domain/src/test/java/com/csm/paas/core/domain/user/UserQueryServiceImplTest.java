package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.entity.UserInfoPageEntity;
import com.csm.paas.core.domain.user.service.impl.UserQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理列表的领域规则（离线，不需要集群/库）。
 * <p>规则来源：厂商 {@code UserService.getUserList}（{@code UserService.java:1828-1867}）。
 * 这些断言的价值在于：厂商把规则写在 service 里，重写版必须**逐条保留**，包括三条看起来像 bug 的：
 * 跳过 {@code root}、`total` 里**仍含** {@code root}、`belongUsers` 装的是 type=2 的角色名。
 */
class UserQueryServiceImplTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);

    private UserQueryServiceImpl service() {
        return new UserQueryServiceImpl(userRepository);
    }

    @Test
    void rootUserIsSkippedButStillCountedInTotal() {
        when(userRepository.queryPageByKeyword(null, 0, 20)).thenReturn(List.of(
                user("u1", "admin"), user("u2", "root")));
        when(userRepository.countByKeyword(null)).thenReturn(2L);

        UserInfoPageEntity page = service().queryUserInfoPage(1, 20, null);

        assertEquals(1, page.getList().size(), "root 必须被跳过");
        assertEquals("admin", page.getList().get(0).getUsername());
        assertEquals(2L, page.getTotal(), "厂商的 total 来自 PageHelper，包含 root —— 照抄，不'修正'");
    }

    @Test
    void blankNicknameBecomesNullAndSubRolesStayNullWhenEmpty() {
        when(userRepository.queryPageByKeyword(null, 0, 20)).thenReturn(List.of(user("u1", "admin")));
        when(userRepository.countByKeyword(null)).thenReturn(1L);
        when(userRepository.querySubRoleNamesByUserId("u1")).thenReturn(List.of());

        var item = service().queryUserInfoPage(1, 20, null).getList().get(0);

        assertNull(item.getNickname(), "空白昵称 → null（不是空串）");
        assertNull(item.getSubRoles(), "子角色为空时保持 null，序列化成 \"subRoles\":null");
    }

    @Test
    void rolesComeFromType1AndBelongUsersFromType2() {
        when(userRepository.queryPageByKeyword(null, 0, 20)).thenReturn(List.of(user("u1", "admin")));
        when(userRepository.countByKeyword(null)).thenReturn(1L);
        when(userRepository.querySubRoleNamesByUserId("u1")).thenReturn(List.of("子角色A"));
        when(userRepository.queryRoleNamesByUsernameAndType("admin", 1)).thenReturn(List.of("运维管理员门户"));
        when(userRepository.queryRoleNamesByUsernameAndType("admin", 2)).thenReturn(List.of("席位操作员"));

        var item = service().queryUserInfoPage(1, 20, null).getList().get(0);

        assertEquals(List.of("运维管理员门户"), item.getRoles());
        // 厂商字段名错位：belongUsers 装的是 g_role.type=2 的名字
        assertEquals(List.of("席位操作员"), item.getBelongUsers());
        assertEquals(List.of("子角色A"), item.getSubRoles());
    }

    @Test
    void keywordIsWrappedAndLengthCheckedAfterWrapping() {
        when(userRepository.queryPageByKeyword("%abc%", 0, 20)).thenReturn(List.of());
        when(userRepository.countByKeyword("%abc%")).thenReturn(0L);

        service().queryUserInfoPage(1, 20, "abc");

        verify(userRepository).queryPageByKeyword("%abc%", 0, 20);
    }

    @Test
    void keywordLongerThan36CharsIsRejected() {
        String tooLong = "a".repeat(37); // 加两个 % → 39 > 38
        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().queryUserInfoPage(1, 20, tooLong));
        assertEquals("user.invalid.search.keyword.length", e.getI18nKey(),
                "必须是厂商的 i18n 键（msg 由消息包解析）");
        verify(userRepository, never()).queryPageByKeyword(anyString(), anyInt(), anyInt());
    }

    @Test
    void thirtySixCharsStillPasses() {
        String boundary = "a".repeat(36); // 加两个 % → 38，不超
        when(userRepository.queryPageByKeyword(eq("%" + boundary + "%"), eq(0), eq(20))).thenReturn(List.of());
        when(userRepository.countByKeyword("%" + boundary + "%")).thenReturn(0L);

        assertTrue(service().queryUserInfoPage(1, 20, boundary).getList().isEmpty());
    }

    @Test
    void emptyKeywordMeansNoFilter() {
        when(userRepository.queryPageByKeyword(null, 0, 20)).thenReturn(List.of());
        when(userRepository.countByKeyword(null)).thenReturn(0L);

        service().queryUserInfoPage(1, 20, "");

        verify(userRepository).queryPageByKeyword(null, 0, 20);
    }

    @Test
    void offsetFollowsPageAndSize() {
        when(userRepository.queryPageByKeyword(null, 20, 20)).thenReturn(List.of());
        when(userRepository.countByKeyword(null)).thenReturn(0L);

        service().queryUserInfoPage(2, 20, null);

        verify(userRepository).queryPageByKeyword(null, 20, 20);
    }

    private static UserEntity user(String id, String username) {
        return UserEntity.builder()
                .id(id).username(username).nickname("  ").status(1)
                .updatedAt(1L).createdAt(1L).lastLoginRole("admin")
                .build();
    }
}
