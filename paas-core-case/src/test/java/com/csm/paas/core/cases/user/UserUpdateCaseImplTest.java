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

    private UserUpdateCaseImpl useCase() {
        return new UserUpdateCaseImpl(userCommandService, userRepository);
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
}
