package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.service.impl.RoleCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
