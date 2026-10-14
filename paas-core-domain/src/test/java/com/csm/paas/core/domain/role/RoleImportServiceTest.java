package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.valobj.ImportRoleRowVO;
import com.csm.paas.core.domain.role.service.impl.RoleImportServiceImpl;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/role/import` 的落库规则（厂商 `RoleService.importRoleExcel:852-1124`）。
 * <p>规格见 `docs/findings/158`。
 */
class RoleImportServiceTest {

    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final ILanguageAutoNamePort autoNamePort = mock(ILanguageAutoNamePort.class);

    private RoleImportServiceImpl service() {
        return new RoleImportServiceImpl(roleRepository, userRepository, subRoleRepository,
                softwareRepository, autoNamePort);
    }

    private ImportRoleRowVO row(String key, String name) {
        return ImportRoleRowVO.builder().rowIndex(1).key(key).name(name).build();
    }

    @Test
    void emptyRowsDoNothing() {
        service().importRoles(List.of(), true);

        verifyNoInteractions(roleRepository, userRepository, subRoleRepository, softwareRepository,
                autoNamePort);
    }

    @Test
    void withoutCoverAnExistingRoleIsSkippedEntirely() {
        when(roleRepository.existsByKeyOrName("maintenance", "维护席位")).thenReturn(true);

        service().importRoles(List.of(row("maintenance", "维护席位")), false);

        verify(roleRepository, never()).insert(any());
        verify(roleRepository, never()).update(any());
        verify(roleRepository, never()).deleteUserRoleByRoleId(anyString());
        verifyNoInteractions(autoNamePort);
    }

    @Test
    void withCoverAnExistingRoleIsRewritten() {
        when(roleRepository.existsByKeyOrName("maintenance", "维护席位")).thenReturn(true);
        when(roleRepository.queryByKeyOrName("maintenance", "维护席位")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-1").key("maintenance").name("维护席位").type(1).build()));
        when(userRepository.queryByUsername("tom")).thenReturn(Optional.of(
                UserEntity.builder().id("u-1").username("tom").build()));
        // tom 还没有 user 战位 ⇒ 先补一条
        when(userRepository.queryRoleKeysByUsername("tom")).thenReturn(List.of());
        when(roleRepository.queryByKey("user")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-user").key("user").build()));
        when(softwareRepository.queryIdByKey("gx-006")).thenReturn(Optional.of("s-1"));
        when(subRoleRepository.queryByKey("subRole01")).thenReturn(Optional.of(
                SubRoleEntity.builder().id("sr-1").key("subRole01").build()));

        var r = ImportRoleRowVO.builder().rowIndex(2).key("maintenance").name("维护席位")
                .desc("新描述").usernames(List.of("tom")).softwareKeys(List.of("gx-006"))
                .subRoleKeys(List.of("subRole01")).nameUs("Maintenance").nameFr("Maintenance")
                .build();
        service().importRoles(List.of(r), true);

        verify(roleRepository).deleteUserRoleByRoleId("r-1");
        verify(roleRepository).deleteRoleSoftwareByRoleId("r-1");
        verify(roleRepository).update(any(RoleEntity.class));
        // tom 先补 user 战位、再关联本战位
        verify(userRepository).insertUserRoleRelation(eq("u-1"), eq("r-user"), anyLong());
        verify(userRepository).insertUserRoleRelation(eq("u-1"), eq("r-1"), anyLong());
        // 软件 order 从 1 起
        verify(roleRepository).insertRoleSoftware(eq("r-1"), eq("s-1"), eq(1), anyLong());
        verify(roleRepository).replaceSubRoleRelationsByRoleId("r-1", List.of("sr-1"));
        // 覆盖分支正常路径**不写**自动名（厂商在子角色处理完后 continue 掉了）
        verify(autoNamePort, never()).saveAutoName(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aNewRoleIsInsertedWithTypeOneAndItsRelations() {
        when(roleRepository.existsByKeyOrName("newrole", "新战位")).thenReturn(false);
        when(roleRepository.queryByKey("newrole")).thenReturn(Optional.of(
                RoleEntity.builder().id("r-new").key("newrole").build()));
        when(userRepository.queryByUsername("tom")).thenReturn(Optional.of(
                UserEntity.builder().id("u-1").username("tom").build()));
        // 已有 user 战位 ⇒ 不再补
        when(userRepository.queryRoleKeysByUsername("tom")).thenReturn(List.of("user"));
        when(softwareRepository.queryIdByKey("gx-006")).thenReturn(Optional.empty());

        var r = ImportRoleRowVO.builder().rowIndex(2).key("newrole").name("新战位")
                .usernames(List.of("tom")).softwareKeys(List.of("gx-006")).subRoleKeys(List.of())
                .nameUs("NewRole").nameFr(null).build();
        service().importRoles(List.of(r), false);

        verify(roleRepository).insert(any(RoleEntity.class));
        verify(roleRepository, never()).queryByKey("user");
        verify(userRepository).insertUserRoleRelation(eq("u-1"), eq("r-new"), anyLong());
        // 软件 key 查不到 ⇒ 跳过
        verify(roleRepository, never()).insertRoleSoftware(anyString(), anyString(), anyInt(), anyLong());
        // 子角色列为空 ⇒ 不做替换
        verify(roleRepository, never()).replaceSubRoleRelationsByRoleId(anyString(), anyList());
        // 新战位分支会写自动名；nameFr 为 null ⇒ 不写
        verify(autoNamePort).saveAutoName("role", "en_US", "newrole", "NewRole");
        verify(autoNamePort, never()).saveAutoName(eq("role"), eq("fr_FR"), anyString(), any());
    }

    @Test
    void missingKeyAndMissingNameUseTheVendorErrorKeys() {
        // cell1 读不到 ⇒ key == null
        var noKey = ImportRoleRowVO.builder().rowIndex(3).name("名字").build();
        WrongArgumentException e1 = assertThrows(WrongArgumentException.class,
                () -> service().importRoles(List.of(noKey), true));
        assertEquals("role.key.is.null.or.format.error", e1.getI18nKey());

        // key 太长 ⇒ role.key.is.illegal（在读 name 之前）
        var longKey = ImportRoleRowVO.builder().rowIndex(3).key("x".repeat(37)).name("名字").build();
        WrongArgumentException e2 = assertThrows(WrongArgumentException.class,
                () -> service().importRoles(List.of(longKey), true));
        assertEquals("role.key.is.illegal", e2.getI18nKey());

        // cell0 读不到 ⇒ name == null
        var noName = ImportRoleRowVO.builder().rowIndex(3).key("k").build();
        WrongArgumentException e3 = assertThrows(WrongArgumentException.class,
                () -> service().importRoles(List.of(noName), true));
        assertEquals("role.name.is.null.or.file.format.error", e3.getI18nKey());

        // name 为空串 ⇒ 被长度检查拦下（厂商那句 role.import.role.key.not.valid 是死代码）
        var blankName = ImportRoleRowVO.builder().rowIndex(3).key("k").name("").build();
        WrongArgumentException e4 = assertThrows(WrongArgumentException.class,
                () -> service().importRoles(List.of(blankName), true));
        assertEquals("role.name.is.illegal", e4.getI18nKey());
    }
}
