package com.csm.paas.core.cases.role;

import com.csm.paas.core.cases.role.impl.RoleDeleteCaseImpl;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.IRoleCommandService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删战位的编排顺序（离线）。
 * <p>**顺序是契约的一部分**：`g_user_role`/`g_sub_role_role` 上有没有级联的外键，
 * 先删 `g_role` 会被数据库拒绝（`docs/findings/23-k1-live-schema-and-unique-constraints.md`）。
 * 厂商顺序见 `RoleService.java:720-725`。
 */
class RoleDeleteCaseImplTest {

    private final IRoleCommandService roleCommandService = mock(IRoleCommandService.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);

    private RoleDeleteCaseImpl useCase() {
        return new RoleDeleteCaseImpl(roleCommandService, roleRepository);
    }

    @Test
    void deletesFiveRelationTablesBeforeTheRoleRowInVendorOrder() {
        when(roleCommandService.validateDeletable(List.of("r1")))
                .thenReturn(List.of(RoleEntity.builder().id("r1").key("user").build()));

        useCase().deleteRoles(List.of("r1"));

        InOrder order = inOrder(roleRepository);
        order.verify(roleRepository).deleteSubRoleRelationsByRoleId("r1");
        order.verify(roleRepository).deleteUserAuditByRoleId("r1");
        order.verify(roleRepository).deleteUserRoleByRoleId("r1");
        order.verify(roleRepository).deleteUserLoginByRoleId("r1");
        order.verify(roleRepository).deleteRoleSoftwareByRoleId("r1");
        order.verify(roleRepository).deleteById("r1");
    }

    @Test
    void validatesBeforeAnyDelete() {
        when(roleCommandService.validateDeletable(List.of("r1"))).thenReturn(List.of());

        useCase().deleteRoles(List.of("r1"));

        verify(roleRepository, never()).deleteById("r1");
        verify(roleRepository, never()).deleteUserRoleByRoleId("r1");
    }

    @Test
    void everyIdInTheBatchIsDeleted() {
        when(roleCommandService.validateDeletable(List.of("a", "b"))).thenReturn(List.of(
                RoleEntity.builder().id("a").key("user").build(),
                RoleEntity.builder().id("b").key("other").build()));

        useCase().deleteRoles(List.of("a", "b"));

        verify(roleRepository).deleteById("a");
        verify(roleRepository).deleteById("b");
    }
}
