package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.service.impl.UserRolesQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/user/list`（厂商 `UserService.list` + `getUserRoles:1780-1803`）。
 * <p>**四组用例已对拉完全相等**（`findings/99`），含 16 键条目与超长 keyword 的 400。
 */
class UserRolesQueryTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IRoleQueryService roleQueryService = mock(IRoleQueryService.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);

    private UserRolesQueryService service() {
        return new UserRolesQueryService(userRepository, roleQueryService, subRoleRepository);
    }

    private void oneUser(String username, String roleKey) {
        when(userRepository.queryPageByKeyword(org.mockito.ArgumentMatchers.<String>any(), anyInt(), anyInt())).thenReturn(List.of(
                UserEntity.builder().id("u1").username(username).status(1).build()));
        when(userRepository.countByKeyword(org.mockito.ArgumentMatchers.<String>any())).thenReturn(1L);
        when(roleQueryService.queryRoleList(anyString(), anyInt(), anyInt())).thenReturn(
                RoleListEntity.builder().list(List.of(RoleEntity.builder().id("r1").key(roleKey).build())).total(1).build());
        when(subRoleRepository.queryByUserId("u1")).thenReturn(List.of());
    }

    @Test
    void buildsVendorShapedItemWithAdminFlagAndEmptySubRoles() {
        oneUser("admin", "admin");

        var item = service().queryUserRolesPage(1, 20, null).getList().get(0);

        assertEquals("admin", item.getUsername());
        assertTrue(item.isAdmin(), "任一角色 key 为 admin ⇒ admin=true");
        assertTrue(item.getSubRoles().isEmpty(), "subRoles 空时是 []（不是 null）");
        assertEquals(1, item.getRoles().size(), "roles 是完整角色对象");
    }

    @Test
    void nonAdminRoleYieldsAdminFalse() {
        oneUser("bob", "user");

        assertFalse(service().queryUserRolesPage(1, 20, null).getList().get(0).isAdmin());
    }

    /** `"%" + keyword + "%"` 长度 > 38 ⇒ 400（照抄厂商"含 % 算长度"）。 */
    @Test
    void tooLongKeywordIsRejected() {
        assertEquals("user.invalid.search.keyword.length",
                assertThrows(WrongArgumentException.class,
                        () -> service().queryUserRolesPage(1, 20, "a".repeat(40))).getI18nKey());
    }
}
