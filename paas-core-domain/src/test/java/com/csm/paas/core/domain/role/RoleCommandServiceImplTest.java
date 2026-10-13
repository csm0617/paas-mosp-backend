package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.impl.RoleCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 删战位的前置校验（离线）。
 * <p>规则来源：厂商 `RoleService.java:711-719`。
 * <p>注意一条**照抄而不是修好**的行为：厂商的保留字常量里有 `cicd`/`root`，但删除路径**只护 `admin`/`dev`**
 * （`RoleConstant.CICD_KEY`/`ROOT_KEY` 零引用），所以 `cicd`/`root` 战位**可以被删掉**。这里锁住该行为。
 */
class RoleCommandServiceImplTest {

    private final IRoleRepository roleRepository = mock(IRoleRepository.class);

    private RoleCommandServiceImpl service() {
        return new RoleCommandServiceImpl(roleRepository);
    }

    @Test
    void missingRoleThrowsVendorKey() {
        when(roleRepository.queryById("nope")).thenReturn(Optional.empty());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().validateDeletable(List.of("nope")));
        assertEquals("role.missing.storage", e.getI18nKey());
    }

    @Test
    void adminAndDevCannotBeDeleted() {
        when(roleRepository.queryById("r1")).thenReturn(Optional.of(role("r1", "admin")));
        WrongArgumentException admin = assertThrows(WrongArgumentException.class,
                () -> service().validateDeletable(List.of("r1")));
        assertEquals("role.cannot.delete.storage", admin.getI18nKey());

        when(roleRepository.queryById("r2")).thenReturn(Optional.of(role("r2", "dev")));
        WrongArgumentException dev = assertThrows(WrongArgumentException.class,
                () -> service().validateDeletable(List.of("r2")));
        assertEquals("role.cannot.delete.storage", dev.getI18nKey());
    }

    @Test
    void cicdAndRootAreNotProtectedInVendorBehavior() {
        when(roleRepository.queryById("r3")).thenReturn(Optional.of(role("r3", "cicd")));
        when(roleRepository.queryById("r4")).thenReturn(Optional.of(role("r4", "root")));

        List<RoleEntity> deletable = service().validateDeletable(List.of("r3", "r4"));

        assertEquals(2, deletable.size(), "厂商只护 admin/dev——cicd/root 照抄为可删");
    }

    @Test
    void ordinaryRoleIsReturnedInInputOrder() {
        when(roleRepository.queryById("a")).thenReturn(Optional.of(role("a", "user")));
        when(roleRepository.queryById("b")).thenReturn(Optional.of(role("b", "other")));

        List<RoleEntity> deletable = service().validateDeletable(List.of("a", "b"));

        assertEquals(List.of("a", "b"), deletable.stream().map(RoleEntity::getId).toList());
    }

    @Test
    void firstInvalidIdStopsTheWholeBatch() {
        when(roleRepository.queryById("ok")).thenReturn(Optional.of(role("ok", "user")));
        when(roleRepository.queryById("bad")).thenReturn(Optional.of(role("bad", "admin")));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().validateDeletable(List.of("ok", "bad")));
        assertEquals("role.cannot.delete.storage", e.getI18nKey(), "校验按入参顺序，第一个不合法的就抛");
    }

    @Test
    void nullIdsIsTreatedAsEmpty() {
        assertEquals(0, service().validateDeletable(null).size());
    }

    private static RoleEntity role(String id, String key) {
        return RoleEntity.builder().id(id).key(key).name("战位").type(1).build();
    }

    // ---------- prepareNewRole（新增）----------

    @Test
    void newRoleRejectedWhenKeyOrNameExists() {
        when(roleRepository.existsByKeyOrName("dup", "名字")).thenReturn(true);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().prepareNewRole(newForm("dup", "名字")));
        assertEquals("role.storage.existed", e.getI18nKey());
        assertEquals(2, e.getArgs().length, "厂商带 (key, name) 两个参数");
    }

    @Test
    void newRoleNameLengthIsValidated() {
        when(roleRepository.existsByKeyOrName("k", "")).thenReturn(false);
        assertEquals("role.name.invalid.length", assertThrows(WrongArgumentException.class,
                () -> service().prepareNewRole(newForm("k", ""))).getI18nKey());

        when(roleRepository.existsByKeyOrName("k", "x".repeat(101))).thenReturn(false);
        assertEquals("role.name.invalid.length", assertThrows(WrongArgumentException.class,
                () -> service().prepareNewRole(newForm("k", "x".repeat(101)))).getI18nKey());
    }

    /**
     * 厂商写的是 `key.length() < 0`（不是 `<1`）⇒ **空 key 也能通过**。
     * 这是那种"看起来是笔误、但改了就与厂商不同"的上界，照抄。
     */
    @Test
    void emptyKeyPassesBecauseVendorChecksLessThanZero() {
        when(roleRepository.existsByKeyOrName("", "名字")).thenReturn(false);

        RoleEntity role = service().prepareNewRole(newForm("", "名字"));

        assertEquals("", role.getKey(), "厂商的上界是 <0，空串能过");
    }

    @Test
    void newRoleDescBlankBecomesNullAndUpdatedAtStaysNull() {
        when(roleRepository.existsByKeyOrName("k", "名字")).thenReturn(false);

        RoleEntity role = service().prepareNewRole(newForm("k", "名字"));

        assertNull(role.getDesc(), "新增时空白 desc → null");
        assertNull(role.getUpdatedAt(), "厂商 addRole 只设 createdAt，不设 updatedAt");
        assertNotNull(role.getCreatedAt());
    }

    // ---------- prepareEditedRole（修改）----------

    @Test
    void editingBuiltinRoleIsRejectedWheneverKeyIsPresent() {
        when(roleRepository.queryById("r-admin")).thenReturn(Optional.of(role("r-admin", "admin")));
        when(roleRepository.queryById("r-dev")).thenReturn(Optional.of(role("r-dev", "dev")));

        // 注意：**哪怕 key 与当前值相同**也不许改（厂商只判 key != null）
        assertEquals("role.can.not.edit.admin.key", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("r-admin", editForm("admin", "新名"))).getI18nKey());
        assertEquals("role.can.not.edit.dev.key", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("r-dev", editForm("dev", "新名"))).getI18nKey());
    }

    @Test
    void builtinRoleCanStillBeEditedWithoutKeyField() {
        when(roleRepository.queryById("r-admin")).thenReturn(Optional.of(role("r-admin", "admin")));
        when(roleRepository.existsByNameExcludingId("新名", "r-admin")).thenReturn(false);

        RoleEntity updated = service().prepareEditedRole("r-admin", editForm(null, "新名"));

        assertEquals("admin", updated.getKey(), "不带 key → key 保持原值");
        assertEquals("新名", updated.getName());
    }

    @Test
    void reservedWordsAreRejected() {
        when(roleRepository.queryById("r1")).thenReturn(Optional.of(role("r1", "user")));

        assertEquals("role.identifier.reserved.word", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("r1", editForm("admin", "名字"))).getI18nKey());
    }

    @Test
    void duplicateKeyAndNameUseTheirOwnKeys() {
        when(roleRepository.queryById("r1")).thenReturn(Optional.of(role("r1", "user")));
        when(roleRepository.existsByKeyExcludingId("taken", "r1")).thenReturn(true);
        assertEquals("role.key.exist", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("r1", editForm("taken", "名字"))).getI18nKey());

        when(roleRepository.existsByKeyExcludingId("fresh", "r1")).thenReturn(false);
        when(roleRepository.existsByNameExcludingId("占用的名字", "r1")).thenReturn(true);
        assertEquals("role.change.name.exist", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("r1", editForm("fresh", "占用的名字"))).getI18nKey());
    }

    @Test
    void editedRoleDescBlankBecomesEmptyStringAndCreatedAtPreserved() {
        RoleEntity existing = role("r1", "user");
        existing.setCreatedAt(123L);
        when(roleRepository.queryById("r1")).thenReturn(Optional.of(existing));
        when(roleRepository.existsByNameExcludingId("名字", "r1")).thenReturn(false);

        RoleEntity updated = service().prepareEditedRole("r1", editForm(null, "名字"));

        assertEquals("", updated.getDesc(), "修改时空白 desc → 空串（新增时是 null）");
        assertEquals(123L, updated.getCreatedAt());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void editingMissingRoleThrows() {
        when(roleRepository.queryById("nope")).thenReturn(Optional.empty());

        assertEquals("role.storage.not.found", assertThrows(WrongArgumentException.class,
                () -> service().prepareEditedRole("nope", editForm(null, "名字"))).getI18nKey());
    }

    private static com.csm.paas.core.domain.role.model.valobj.RoleFormVO newForm(String key, String name) {
        return com.csm.paas.core.domain.role.model.valobj.RoleFormVO.builder()
                .key(key).name(name).type(1).build();
    }

    private static com.csm.paas.core.domain.role.model.valobj.RoleFormVO editForm(String key, String name) {
        return newForm(key, name);
    }
}
