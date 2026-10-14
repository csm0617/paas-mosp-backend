package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.impl.RoleCommandServiceImpl;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 战位关联写四个端点的领域规则（厂商 `RoleController:337-411`）：
 * `POST /{roleId}/users`、`POST /{roleId}/suRoles`、`DELETE /{roleId}/{userId}`、
 * `DELETE /{roleId}/app/{appId}`。规格见 `docs/findings/156`。
 */
class RoleRelationCommandTest {

    private static final String ADMIN_ROLE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ADMIN_USER_ID = "22222222-2222-2222-2222-222222222222";

    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);
    private final IRoleOnlineUserRepository onlineUserRepository = mock(IRoleOnlineUserRepository.class);

    private RoleCommandServiceImpl service() {
        return new RoleCommandServiceImpl(roleRepository, userRepository, subRoleRepository,
                onlineUserRepository);
    }

    private void adminRoleExists() {
        when(roleRepository.queryById(ADMIN_ROLE_ID)).thenReturn(Optional.of(
                RoleEntity.builder().id(ADMIN_ROLE_ID).key("admin").name("运维管理员门户").build()));
    }

    @Test
    void adminRoleForcesAdminUserIntoTheList() {
        adminRoleExists();
        when(userRepository.queryByUsername("admin")).thenReturn(Optional.of(
                UserEntity.builder().id(ADMIN_USER_ID).username("admin").build()));
        when(roleRepository.queryFullUsersByRoleId(ADMIN_ROLE_ID)).thenReturn(List.of());

        service().relateUsers(ADMIN_ROLE_ID, List.of("u-1"));

        // 厂商：admin 战位会把 admin 用户也塞进去（UserService.usersRelateRole:774-776）
        verify(roleRepository).replaceUserRelationsByRoleId(ADMIN_ROLE_ID, List.of("u-1", ADMIN_USER_ID));
    }

    @Test
    void plainRoleKeepsUserIdsAsIs() {
        when(roleRepository.queryById("r-2")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-2").key("user").build()));
        when(roleRepository.queryFullUsersByRoleId("r-2")).thenReturn(List.of());

        service().relateUsers("r-2", List.of("u-1", "u-2"));

        verify(userRepository, never()).queryByUsername(anyString());
        verify(roleRepository).replaceUserRelationsByRoleId("r-2", List.of("u-1", "u-2"));
    }

    @Test
    void missingRoleIsRejected() {
        when(roleRepository.queryById("nope")).thenReturn(Optional.empty());

        WrongArgumentException ex = assertThrows(WrongArgumentException.class,
                () -> service().relateUsers("nope", List.of()));
        assertEquals("role.storage.not.found", ex.getI18nKey());
        verifyNoInteractions(onlineUserRepository);
    }

    @Test
    void relateSubRolesUsesTheTouchingVariantAndReturnsSubRoleList() {
        when(roleRepository.queryById("r-3")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-3").key("user").build()));
        when(subRoleRepository.queryByIds(List.of("s-1", "s-2"))).thenReturn(List.of(
                SubRoleEntity.builder().id("s-1").key("k1").build(),
                SubRoleEntity.builder().id("s-2").key("k2").build()));
        when(roleRepository.querySubRoleIdsByRoleId("r-3")).thenReturn(List.of("s-1", "s-2"));

        RoleEntity result = service().relateSubRoles("r-3", List.of("s-1", "s-2"));

        // 厂商这条路径走 roleRelateSubRoles ⇒ 会覆盖子角色 created_at（与 POST /v1/role 不同）
        verify(roleRepository).replaceSubRoleRelationsTouchingCreatedAt("r-3", List.of("s-1", "s-2"));
        verify(roleRepository, never()).replaceSubRoleRelationsByRoleId(anyString(), anyList());
        assertEquals(2, result.getSubRoleList().size());
    }

    @Test
    void cannotRemoveAdminUserFromAdminRole() {
        adminRoleExists();
        when(userRepository.queryById(ADMIN_USER_ID)).thenReturn(Optional.of(
                UserEntity.builder().id(ADMIN_USER_ID).username("admin").build()));

        WrongArgumentException ex = assertThrows(WrongArgumentException.class,
                () -> service().removeUserFromRole(ADMIN_ROLE_ID, ADMIN_USER_ID));
        assertEquals("user.cannot.remove.ofc.admin", ex.getI18nKey());
        verify(roleRepository, never()).deleteUserRoleRelation(anyString(), anyString());
        verifyNoInteractions(onlineUserRepository);
    }

    @Test
    void removingANormalUserEvictsTheSessionThenDeletesTheRow() {
        when(roleRepository.queryById("r-9")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-9").key("user").build()));
        when(userRepository.queryById("u-9")).thenReturn(Optional.of(
                UserEntity.builder().id("u-9").username("tom").build()));
        when(roleRepository.queryFullUsersByRoleId("r-9")).thenReturn(List.of());

        service().removeUserFromRole("r-9", "u-9");

        verify(onlineUserRepository).evictSessionIfOnline("tom", "user");
        verify(roleRepository).deleteUserRoleRelation("r-9", "u-9");
    }

    @Test
    void unknownUserIsRejectedBeforeTouchingRedis() {
        when(roleRepository.queryById("r-9")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-9").key("user").build()));
        when(userRepository.queryById("ghost")).thenReturn(Optional.empty());

        WrongArgumentException ex = assertThrows(WrongArgumentException.class,
                () -> service().removeUserFromRole("r-9", "ghost"));
        assertEquals("user.id.not.found", ex.getI18nKey());
        verifyNoInteractions(onlineUserRepository);
    }

    @Test
    void removeAppFromRoleDeletesOnlyThatRow() {
        when(roleRepository.queryById("r-4")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-4").key("user").build()));

        service().removeAppFromRole("r-4", "app-7");

        verify(roleRepository).deleteSoftwareRelation(eq("r-4"), eq("app-7"));
        verify(roleRepository, never()).deleteRoleSoftwareByRoleId(anyString());
    }
}
