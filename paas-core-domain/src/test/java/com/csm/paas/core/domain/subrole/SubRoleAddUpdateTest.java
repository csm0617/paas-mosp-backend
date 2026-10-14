package com.csm.paas.core.domain.subrole;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/sub_role` 与 `PUT /v1/sub_role/update`
 * （厂商 `SubRoleController.addRole:59-70` + `SubRoleServiceImpl.addRole:113-140` / `updateSuRole:233-260`）。
 * <p>规格见 `docs/findings/148`。
 */
class SubRoleAddUpdateTest {

    private final ISubRoleRepository repository = mock(ISubRoleRepository.class);

    private SubRoleCommandServiceImpl service() {
        return new SubRoleCommandServiceImpl(repository);
    }

    private static SubRoleFormVO form(String key, String name) {
        return SubRoleFormVO.builder().key(key).name(name).build();
    }

    // ---------- add ----------

    @Test
    void addRejectsDuplicateAgainstRoleTable() {
        when(repository.existsInRoleByKeyOrName("dev", "开发")).thenReturn(true);
        var e = assertThrows(WrongArgumentException.class, () -> service().addSubRole(form("dev", "开发")));
        assertEquals("sub.role.storage.existed", e.getI18nKey());
        assertEquals(2, e.getArgs().length);
        verify(repository, never()).insertSubRole(anyString(), anyString(), anyString(), any(), anyLong(), anyLong());
    }

    @Test
    void addValidatesNameAndKeyLength() {
        when(repository.existsInRoleByKeyOrName(any(), any())).thenReturn(false);
        assertEquals("sub.role.name.invalid.length",
                assertThrows(WrongArgumentException.class, () -> service().addSubRole(form("k", ""))).getI18nKey());
        assertEquals("sub.role.unique.identifier.invalid.length",
                assertThrows(WrongArgumentException.class, () -> service().addSubRole(form("", "n"))).getI18nKey());
        assertEquals("sub.role.name.invalid.length",
                assertThrows(WrongArgumentException.class,
                        () -> service().addSubRole(form("k", "x".repeat(101)))).getI18nKey());
    }

    @Test
    void addStoresNullForBlankDescAndRejectsLongDesc() {
        when(repository.existsInRoleByKeyOrName(any(), any())).thenReturn(false);
        when(repository.queryById(anyString())).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));

        service().addSubRole(SubRoleFormVO.builder().key("k").name("n").desc("   ").build());

        @SuppressWarnings("unchecked")
        var descCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertSubRole(anyString(), eq("k"), eq("n"), descCaptor.capture(), anyLong(), anyLong());
        assertNull(descCaptor.getValue(), "desc 空白 ⇒ 存 null");

        assertEquals("sub.role.desc.invalid.length",
                assertThrows(WrongArgumentException.class, () -> service().addSubRole(
                        SubRoleFormVO.builder().key("k").name("n").desc("d".repeat(256)).build())).getI18nKey());
    }

    @Test
    void addRelatesOnlyExistingUsers() {
        when(repository.existsInRoleByKeyOrName(any(), any())).thenReturn(false);
        when(repository.queryById(anyString())).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));
        when(repository.userExists("u1")).thenReturn(true);
        when(repository.userExists("ghost")).thenReturn(false);

        service().addSubRole(SubRoleFormVO.builder().key("k").name("n")
                .userList(List.of("u1", "ghost", "u1")).build());

        verify(repository).deleteUserSubRolesBySubRoleId(anyString());
        verify(repository).insertUserSubRole(anyString(), eq("u1"), anyString(), anyLong(), anyLong());
        verify(repository, never()).insertUserSubRole(anyString(), eq("ghost"), anyString(), anyLong(), anyLong());
    }

    // ---------- update ----------

    @Test
    void updateMissingIdIs400() {
        when(repository.queryById("ghost")).thenReturn(Optional.empty());
        var e = assertThrows(WrongArgumentException.class,
                () -> service().updateSubRole(SubRoleFormVO.builder().id("ghost").build()));
        assertEquals("sub.role.storage.not.found", e.getI18nKey());
    }

    @Test
    void updateRejectsDuplicateKeyAndName() {
        when(repository.queryById("id1")).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));
        when(repository.existsKeyExcludingId("dup", "id1")).thenReturn(true);
        assertEquals("sub.role.key.exist", assertThrows(WrongArgumentException.class,
                () -> service().updateSubRole(SubRoleFormVO.builder().id("id1").key("dup").build())).getI18nKey());

        when(repository.existsNameExcludingId("dup", "id1")).thenReturn(true);
        assertEquals("sub.role.change.name.exist", assertThrows(WrongArgumentException.class,
                () -> service().updateSubRole(SubRoleFormVO.builder().id("id1").name("dup").build())).getI18nKey());
    }

    @Test
    void updateRebuildsUserRelationsAndUpdatesSelectively() {
        when(repository.queryById("id1")).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));
        when(repository.userExists("u1")).thenReturn(true);
        when(repository.updateSubRoleSelective(eq("id1"), eq("k2"), eq("n2"), any(), anyLong())).thenReturn(1);

        assertTrue(service().updateSubRole(SubRoleFormVO.builder()
                .id("id1").key("k2").name("n2").userList(List.of("u1")).build()));

        verify(repository).deleteUserSubRolesBySubRoleId("id1");
        verify(repository).insertUserSubRole(anyString(), eq("u1"), eq("id1"), anyLong(), anyLong());
    }

    @Test
    void updateWithNullUserListDoesNotBlowUp() {
        when(repository.queryById("id1")).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));
        when(repository.updateSubRoleSelective(anyString(), any(), any(), any(), anyLong())).thenReturn(1);

        // 厂商 `LinkedHashSet.addAll(null)` 会 NPE；我们按空列表处理（B24）
        assertTrue(service().updateSubRole(SubRoleFormVO.builder().id("id1").name("n2").build()));
        verify(repository).deleteUserSubRolesBySubRoleId("id1");
        verify(repository, never()).insertUserSubRole(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void userAddRoleWipesThenInsertsWithoutExistenceChecks() {
        assertTrue(service().userAddRole("u1", List.of("sr1", "ghost")));

        verify(repository).deleteUserSubRolesByUserId("u1");
        // 不校验子角色是否存在 ⇒ 两个都插
        verify(repository).insertUserSubRole(anyString(), eq("u1"), eq("sr1"), anyLong(), anyLong());
        verify(repository).insertUserSubRole(anyString(), eq("u1"), eq("ghost"), anyLong(), anyLong());
        verify(repository, never()).userExists(anyString());
    }

    @Test
    void userAddRoleWithNullListOnlyWipes() {
        assertTrue(service().userAddRole("u1", null));
        verify(repository).deleteUserSubRolesByUserId("u1");
        verify(repository, never()).insertUserSubRole(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void roleAddUserWipesThenInsertsWithoutExistenceChecks() {
        assertTrue(service().roleAddUser("sr1", List.of("u1", "ghost")));

        verify(repository).deleteUserSubRolesBySubRoleId("sr1");
        verify(repository).insertUserSubRole(anyString(), eq("u1"), eq("sr1"), anyLong(), anyLong());
        verify(repository).insertUserSubRole(anyString(), eq("ghost"), eq("sr1"), anyLong(), anyLong());
        verify(repository, never()).userExists(anyString());
    }

    @Test
    void updateValidatesNameKeyLengthWhenPresent() {
        when(repository.queryById("id1")).thenReturn(Optional.of(SubRoleEntity.builder().id("id1").build()));
        assertEquals("sub.role.change.name.invalid", assertThrows(WrongArgumentException.class,
                () -> service().updateSubRole(SubRoleFormVO.builder().id("id1").name("x".repeat(101)).build()))
                .getI18nKey());
        assertEquals("sub.role.change.key.name.invalid", assertThrows(WrongArgumentException.class,
                () -> service().updateSubRole(SubRoleFormVO.builder().id("id1").key("k".repeat(37)).build()))
                .getI18nKey());
    }
}
