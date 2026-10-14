package com.csm.paas.core.domain.subrole;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleCommandServiceImpl;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/sub_role/{id}` 与 `DELETE /v1/sub_role/delete`
 * （厂商 `SubRoleController.subRoleDetail:163-174` + `SubRoleServiceImpl.detail:341-344` / `deleteByIds:277-291`）。
 * <p>规格见 `docs/findings/147`。
 */
class SubRoleDetailAndDeleteTest {

    private final ISubRoleRepository repository = mock(ISubRoleRepository.class);

    private SubRoleQueryServiceImpl queryService() {
        return new SubRoleQueryServiceImpl(repository);
    }

    private SubRoleCommandServiceImpl commandService() {
        return new SubRoleCommandServiceImpl(repository);
    }

    @Test
    void detailFillsNameFallbacksAndLeavesListsNull() {
        when(repository.queryById("sr1")).thenReturn(Optional.of(
                SubRoleEntity.builder().id("sr1").key("dev").name("开发").build()));

        var role = queryService().querySubRoleDetail("sr1");

        assertEquals("开发", role.getNameUs());
        assertEquals("开发", role.getNameFr());
        assertNull(role.getUserList());
        assertNull(role.getRoleList());
    }

    @Test
    void detailReturnsNullWhenAbsent() {
        when(repository.queryById("ghost")).thenReturn(Optional.empty());
        assertNull(queryService().querySubRoleDetail("ghost"));
    }

    @Test
    void deleteRemovesThreePlacesPerId() {
        when(repository.queryById("sr1")).thenReturn(Optional.of(
                SubRoleEntity.builder().id("sr1").key("dev").name("开发").build()));

        assertTrue(commandService().deleteSubRoles(List.of("sr1")));

        verify(repository).deleteSubRoleRolesBySubRoleId("sr1");
        verify(repository).deleteById("sr1");
        verify(repository).deleteUserSubRolesBySubRoleId("sr1");
    }

    @Test
    void deleteWorksForUnknownIdAndIsAlwaysTrue() {
        when(repository.queryById("ghost")).thenReturn(Optional.empty());

        assertTrue(commandService().deleteSubRoles(List.of("ghost")));

        verify(repository).deleteSubRoleRolesBySubRoleId("ghost");
        verify(repository).deleteById("ghost");
        verify(repository).deleteUserSubRolesBySubRoleId("ghost");
    }

    @Test
    void deleteNullListIsTrueWithoutTouchingDb() {
        assertTrue(commandService().deleteSubRoles(null));
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void deleteHandlesMultipleIdsInOrder() {
        when(repository.queryById(anyString())).thenReturn(Optional.empty());

        commandService().deleteSubRoles(List.of("a", "b"));

        var order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).deleteById("a");
        order.verify(repository).deleteById("b");
    }
}
