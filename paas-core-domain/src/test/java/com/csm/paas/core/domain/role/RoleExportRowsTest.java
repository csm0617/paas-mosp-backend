package com.csm.paas.core.domain.role;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleOnlineUserRepository;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.domain.role.service.impl.RoleQueryServiceImpl;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/role/backup` 的行数据（厂商 `RoleService.generateExportExcel:784-830`）。
 * <p>规格见 `docs/findings/157`。
 */
class RoleExportRowsTest {

    private final IRoleRepository repository = mock(IRoleRepository.class);
    private final ISubRoleRepository subRoleRepository = mock(ISubRoleRepository.class);
    private final ILanguageAutoNamePort autoNamePort = mock(ILanguageAutoNamePort.class);

    private RoleQueryServiceImpl service() {
        return new RoleQueryServiceImpl(repository, mock(IRoleOnlineUserRepository.class),
                subRoleRepository, autoNamePort);
    }

    @Test
    void adminDevAndUserAreExcludedAndColumnsAreFilled() {
        when(repository.queryByIdsOrderByCreatedAtDesc(List.of())).thenReturn(List.of(
                RoleEntity.builder().id("r-admin").key("admin").name("运维管理员门户").build(),
                RoleEntity.builder().id("r-dev").key("dev").name("开发管理员门户").build(),
                RoleEntity.builder().id("r-user").key("user").name("席位操作员").build(),
                RoleEntity.builder().id("r-1").key("maintenance").name("维护席位").desc("描述").build()));
        when(repository.querySoftwareKeysByRoleId("r-1")).thenReturn(List.of("gx-006"));
        when(repository.querySubRoleIdsByRoleId("r-1")).thenReturn(List.of("s-1"));
        when(subRoleRepository.queryByIds(List.of("s-1"))).thenReturn(List.of(
                SubRoleEntity.builder().id("s-1").key("subRole01").build()));
        when(repository.queryUsersByRoleId("r-1")).thenReturn(List.of(
                RoleUserVO.builder().userId("u-1").userName("user1").build()));
        when(autoNamePort.queryAutoName("role", "en_US", "maintenance")).thenReturn(Optional.of("Maintenance"));
        when(autoNamePort.queryAutoName("role", "fr_FR", "maintenance")).thenReturn(Optional.empty());

        var rows = service().queryExportRows(List.of());

        // 三个内置战位被过滤掉，只剩一条
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals("维护席位", row.getName());
        assertEquals("maintenance", row.getKey());
        assertEquals(List.of("gx-006"), row.getSoftwareKeys());
        assertEquals(List.of("subRole01"), row.getSubRoleKeys());
        assertEquals("描述", row.getDesc());
        assertEquals(List.of("user1"), row.getUsernames());
        assertEquals("Maintenance", row.getNameUs());
        assertNull(row.getNameFr(), "自动名取不到就是 null（厂商 getAutoNameMessage 返回 null）");
    }

    @Test
    void idsArePassedThroughAndEmptySubRolesSkipTheLookup() {
        when(repository.queryByIdsOrderByCreatedAtDesc(List.of("r-9"))).thenReturn(List.of(
                RoleEntity.builder().id("r-9").key("k9").name("n9").build()));
        when(repository.querySoftwareKeysByRoleId("r-9")).thenReturn(List.of());
        when(repository.querySubRoleIdsByRoleId("r-9")).thenReturn(List.of());
        when(repository.queryUsersByRoleId("r-9")).thenReturn(List.of());

        var rows = service().queryExportRows(List.of("r-9"));

        verify(repository).queryByIdsOrderByCreatedAtDesc(List.of("r-9"));
        verify(subRoleRepository, never()).queryByIds(org.mockito.ArgumentMatchers.anyList());
        assertEquals(List.of(), rows.get(0).getSubRoleKeys());
        assertEquals(List.of(), rows.get(0).getUsernames());
    }
}
