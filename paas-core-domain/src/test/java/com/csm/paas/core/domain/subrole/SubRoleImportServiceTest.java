package com.csm.paas.core.domain.subrole;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.valobj.ImportSubRoleRowVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO;
import com.csm.paas.core.domain.subrole.service.ISubRoleCommandService;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleImportServiceImpl;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/sub_role/import` 的落库规则（厂商 `SubRoleServiceImpl.importSubroleExcel:583-625`）。
 * <p>规格见 `docs/findings/158`。
 */
class SubRoleImportServiceTest {

    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);
    private final ISubRoleCommandService commandService = mock(ISubRoleCommandService.class);
    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IRoleRepository roleRepository = mock(IRoleRepository.class);
    private final ILanguageAutoNamePort autoNamePort = mock(ILanguageAutoNamePort.class);

    private SubRoleImportServiceImpl service() {
        return new SubRoleImportServiceImpl(subRoleRepository, commandService, userRepository,
                roleRepository, autoNamePort);
    }

    private ImportSubRoleRowVO row(String key, String name) {
        return ImportSubRoleRowVO.builder().rowIndex(1).key(key).name(name).build();
    }

    @Test
    void emptyRowsDoNothing() {
        service().importSubRoles(List.of(), true);

        verifyNoInteractions(subRoleRepository, commandService, userRepository, roleRepository,
                autoNamePort);
    }

    @Test
    void withoutCoverAnExistingSubRoleIsSkipped() {
        when(subRoleRepository.existsInRoleByKeyOrName("sr1", "子角色1")).thenReturn(true);

        service().importSubRoles(List.of(row("sr1", "子角色1")), false);

        verify(commandService, never()).addSubRole(any());
        verify(subRoleRepository, never()).updateDesc(anyString(), any());
        verifyNoInteractions(autoNamePort);
    }

    @Test
    void aNewSubRoleIsCreatedAndItsRelationsAreRewritten() {
        when(subRoleRepository.existsInRoleByKeyOrName("sr1", "子角色1")).thenReturn(false);
        when(commandService.addSubRole(any(SubRoleFormVO.class))).thenReturn(
                SubRoleEntity.builder().id("id-1").key("sr1").build());
        when(subRoleRepository.queryByKey("sr1")).thenReturn(Optional.of(
                SubRoleEntity.builder().id("id-1").key("sr1").build()));
        when(userRepository.queryByUsername("tom")).thenReturn(Optional.of(
                UserEntity.builder().id("u-1").username("tom").build()));
        when(roleRepository.queryRoleIdsByKeys(List.of("admin"))).thenReturn(List.of("r-admin"));

        var r = ImportSubRoleRowVO.builder().rowIndex(2).key("sr1").name("子角色1").desc("描述")
                .usernames(List.of("tom")).roleKeys(List.of("admin"))
                .nameUs("SubRoleOne").nameFr(null).build();
        service().importSubRoles(List.of(r), false);

        verify(commandService).addSubRole(any(SubRoleFormVO.class));
        verify(subRoleRepository).updateDesc("id-1", "描述");
        verify(subRoleRepository).replaceUserSubRoleRelations("id-1", List.of("u-1"));
        verify(subRoleRepository).replaceSubRoleRoleRelations("id-1", List.of("r-admin"));
        // 照抄：关联战位时把战位的 updated_at 覆盖成 now
        verify(roleRepository).touchUpdatedAt(eq("r-admin"), anyLong());
        verify(autoNamePort).saveAutoName("sub_role", "en_US", "sr1", "SubRoleOne");
        verify(autoNamePort, never()).saveAutoName(eq("sub_role"), eq("fr_FR"), anyString(), any());
    }

    @Test
    void withCoverAnExistingSubRoleIsUpdatedButNotRecreated() {
        when(subRoleRepository.existsInRoleByKeyOrName("sr1", "子角色1")).thenReturn(true);
        when(subRoleRepository.queryByKey("sr1")).thenReturn(Optional.of(
                SubRoleEntity.builder().id("id-1").key("sr1").build()));

        service().importSubRoles(List.of(row("sr1", "子角色1")), true);

        verify(commandService, never()).addSubRole(any());
        verify(subRoleRepository).updateDesc("id-1", null);
        verify(subRoleRepository, never()).replaceUserSubRoleRelations(anyString(), any());
        verify(subRoleRepository, never()).replaceSubRoleRoleRelations(anyString(), any());
    }

    @Test
    void missingNameAndKeyUseTheVendorErrorKeys() {
        var noName = ImportSubRoleRowVO.builder().rowIndex(3).key("k").build();
        assertEquals("subrole.name.is.null.or.file.format.error",
                assertThrows(WrongArgumentException.class,
                        () -> service().importSubRoles(List.of(noName), true)).getI18nKey());

        var longName = ImportSubRoleRowVO.builder().rowIndex(3).key("k").name("x".repeat(129)).build();
        assertEquals("subrole.name.is.illegal",
                assertThrows(WrongArgumentException.class,
                        () -> service().importSubRoles(List.of(longName), true)).getI18nKey());

        var noKey = ImportSubRoleRowVO.builder().rowIndex(3).name("名字").build();
        assertEquals("subrole.key.is.null.or.format.error",
                assertThrows(WrongArgumentException.class,
                        () -> service().importSubRoles(List.of(noKey), true)).getI18nKey());

        var longKey = ImportSubRoleRowVO.builder().rowIndex(3).key("x".repeat(37)).name("名字").build();
        assertEquals("subrole.key.is.illegal",
                assertThrows(WrongArgumentException.class,
                        () -> service().importSubRoles(List.of(longKey), true)).getI18nKey());
    }
}
