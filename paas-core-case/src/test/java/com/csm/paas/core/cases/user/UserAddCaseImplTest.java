package com.csm.paas.core.cases.user;

import com.csm.paas.core.cases.user.impl.UserAddCaseImpl;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新增用户的编排顺序（离线）。
 * <p>厂商顺序：插用户 → `userListKey`（角色 type=2）→ `roleListKey`（战位 type=1）→ `subRoleListId`
 * （`UserService.java:514-550`）。
 * <p>另锁住一条**对厂商缺陷的修正**：厂商 `roleListKey` 为 null 时在 `:545` 抛 NPE
 * （用户行已插入 → 半成品用户）；这里必须**按空处理且不抛**。
 */
class UserAddCaseImplTest {

    private final IUserCommandService userCommandService = mock(IUserCommandService.class);
    private final IUserRepository userRepository = mock(IUserRepository.class);

    private UserAddCaseImpl useCase() {
        return new UserAddCaseImpl(userCommandService, userRepository);
    }

    @Test
    void insertsUserThenRolesThenPositionsThenSubRoles() {
        UserEntity prepared = UserEntity.builder().id("u1").username("bob").build();
        when(userCommandService.prepareNewUser(org.mockito.ArgumentMatchers.any())).thenReturn(prepared);
        when(userRepository.queryRoleIdsByKeys(List.of("roleKey"))).thenReturn(List.of("r-role"));
        when(userRepository.queryRoleIdsByKeys(List.of("posKey"))).thenReturn(List.of("r-pos"));

        UserFormVO form = UserFormVO.builder()
                .userListKey(List.of("roleKey"))
                .roleListKey(List.of("posKey"))
                .subRoleListId(List.of("sr-1"))
                .build();
        useCase().addUser(form);

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).insert(prepared);
        order.verify(userRepository).replaceUserRoleRelations("u1", 2, List.of("r-role"));
        order.verify(userRepository).replaceUserRoleRelations("u1", 1, List.of("r-pos"));
        order.verify(userRepository).replaceUserSubRoleRelations("u1", List.of("sr-1"));
    }

    @Test
    void nullRoleListKeyDoesNotBlowUp() {
        // 厂商这里会 NPE（UserService.java:545）并留下半成品用户；我们按空处理
        when(userCommandService.prepareNewUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());

        UserFormVO form = UserFormVO.builder().build();
        useCase().addUser(form);

        verify(userRepository).insert(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).replaceUserRoleRelations(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void emptyKeysSkipRelationWrites() {
        when(userCommandService.prepareNewUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());

        UserFormVO form = UserFormVO.builder().roleListKey(List.of()).userListKey(List.of()).build();
        useCase().addUser(form);

        verify(userRepository, never()).replaceUserRoleRelations(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void unknownRoleKeysAreSkipped() {
        when(userCommandService.prepareNewUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UserEntity.builder().id("u1").username("bob").build());
        // 仓库层已把"按 key 找不到"的过滤掉了
        when(userRepository.queryRoleIdsByKeys(List.of("nope"))).thenReturn(List.of());

        useCase().addUser(UserFormVO.builder().roleListKey(List.of("nope")).build());

        verify(userRepository).replaceUserRoleRelations("u1", 1, List.of());
    }

    @Test
    void returnsTheCreatedUser() {
        UserEntity prepared = UserEntity.builder().id("u1").username("bob").build();
        when(userCommandService.prepareNewUser(org.mockito.ArgumentMatchers.any())).thenReturn(prepared);

        assertEquals(prepared, useCase().addUser(UserFormVO.builder().build()));
    }
}
