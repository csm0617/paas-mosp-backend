package com.csm.paas.core.cases.user;

import com.csm.paas.core.cases.user.impl.UserUpdateCaseImpl;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 修改用户的编排（离线）。
 * <p>与"新增"相反，这里是**空即删除**（厂商 `UserService.update`，`:636-678`）：
 * `userListKey`/`roleListKey` 为 null 或空 → 清掉该类型关联；`subRoleListId` 为空 → 清掉全部子角色关联。
 */
class UserUpdateCaseImplTest {

    private final IUserCommandService userCommandService = mock(IUserCommandService.class);
    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final com.csm.paas.core.domain.role.adapter.repository.IRoleRepository roleRepository =
            mock(com.csm.paas.core.domain.role.adapter.repository.IRoleRepository.class);

    private UserUpdateCaseImpl useCase() {
        return new UserUpdateCaseImpl(userCommandService, userRepository, roleRepository);
    }

    @Test
    void updatesRowThenReplacesRolesPositionsAndSubRoles() {
        UserEntity prepared = UserEntity.builder().id("u1").username("bob").build();
        when(userCommandService.prepareUpdatedUser(eq("u1"), any())).thenReturn(prepared);
        when(userRepository.queryRoleIdsByKeys(List.of("roleKey"))).thenReturn(List.of("r-role"));
        when(userRepository.queryRoleIdsByKeys(List.of("posKey"))).thenReturn(List.of("r-pos"));

        useCase().updateUser("u1", UserFormVO.builder()
                .userListKey(List.of("roleKey")).roleListKey(List.of("posKey"))
                .subRoleListId(List.of("sr-1")).build());

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).update(prepared);
        order.verify(userRepository).replaceUserRoleRelations("u1", 2, List.of("r-role"));
        order.verify(userRepository).replaceUserRoleRelations("u1", 1, List.of("r-pos"));
        order.verify(userRepository).replaceUserSubRoleRelations("u1", List.of("sr-1"));
    }

    @Test
    void nullOrEmptyKeysClearThatTypeOfRelation() {
        when(userCommandService.prepareUpdatedUser(eq("u1"), any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());

        // roleListKey 为 null、userListKey 为空列表 → 两者都清空
        useCase().updateUser("u1", UserFormVO.builder().userListKey(List.of()).build());

        verify(userRepository).replaceUserRoleRelations("u1", 2, List.of());
        verify(userRepository).replaceUserRoleRelations("u1", 1, List.of());
    }

    @Test
    void emptySubRoleListClearsAllSubRoleRelations() {
        when(userCommandService.prepareUpdatedUser(eq("u1"), any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());

        useCase().updateUser("u1", UserFormVO.builder().build());

        verify(userRepository).replaceUserSubRoleRelations("u1", List.of());
    }

    @Test
    void unknownRoleKeysEndUpAsEmptyReplacement() {
        when(userCommandService.prepareUpdatedUser(eq("u1"), any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());
        when(userRepository.queryRoleIdsByKeys(List.of("nope"))).thenReturn(List.of());

        useCase().updateUser("u1", UserFormVO.builder().roleListKey(List.of("nope")).build());

        verify(userRepository).queryRoleIdsByKeys(List.of("nope"));
        verify(userRepository).replaceUserRoleRelations("u1", 1, List.of());
    }

    // ---------- userToRole / {userId}/roles ----------

    @Test
    void userToRoleReplacesAllRelationsAndDeduplicates() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(
                UserEntity.builder().id("u1").username("bob").build()));

        boolean result = useCase().replaceUserRoles("u1", List.of("r1", "r2", "r1"));

        org.junit.jupiter.api.Assertions.assertTrue(result, "厂商恒返回 true");
        // 去重后按入参顺序（厂商用 HashSet，顺序不确定；我们保留顺序以便可复现）
        verify(userRepository).replaceAllUserRoleRelations("u1", List.of("r1", "r2"));
    }

    @Test
    void userToRoleWithEmptyListOnlyDeletes() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(
                UserEntity.builder().id("u1").username("bob").build()));

        useCase().replaceUserRoles("u1", List.of());

        verify(userRepository).replaceAllUserRoleRelations("u1", List.of());
    }

    @Test
    void userToRoleRejectsMissingUser() {
        when(userRepository.queryById("nope")).thenReturn(java.util.Optional.empty());

        com.csm.paas.core.types.exception.WrongArgumentException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.csm.paas.core.types.exception.WrongArgumentException.class,
                        () -> useCase().replaceUserRoles("nope", List.of("r1")));
        org.junit.jupiter.api.Assertions.assertEquals("user.missing.user.info", e.getI18nKey());
    }

    @Test
    void relatePositionsOnlyTouchesType1AndSkipsUnknownKeys() {
        when(userRepository.queryById("u1")).thenReturn(java.util.Optional.of(
                UserEntity.builder().id("u1").username("bob").build()));
        when(roleRepository.filterExistingIds(List.of("r-admin", "r-nope"))).thenReturn(List.of("r-admin"));

        useCase().relatePositions("u1", List.of("r-admin", "r-nope"));

        verify(userRepository).replaceUserRoleRelations("u1", 1, List.of("r-admin"));
    }

    @Test
    void relatePositionsRejectsMissingUserWithItsOwnKey() {
        when(userRepository.queryById("nope")).thenReturn(java.util.Optional.empty());

        com.csm.paas.core.types.exception.WrongArgumentException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.csm.paas.core.types.exception.WrongArgumentException.class,
                        () -> useCase().relatePositions("nope", List.of("r1")));
        org.junit.jupiter.api.Assertions.assertEquals("user.not.found", e.getI18nKey(),
                "这个端点用的是 user.not.found，与 userToRole 的 user.missing.user.info 不同");
    }
}
