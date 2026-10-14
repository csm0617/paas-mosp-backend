package com.csm.paas.core.domain.subrole;

import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.service.impl.SubRoleQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/sub_role/export` 的行数据（厂商 `generateExportExcel:470-512`）。
 * <p>规格见 `docs/findings/154`。
 */
class SubRoleExportRowsTest {

    private final ISubRoleRepository repository = mock(ISubRoleRepository.class);

    private SubRoleQueryServiceImpl service() {
        return new SubRoleQueryServiceImpl(repository);
    }

    @Test
    void emptyIdsMeansAllRowsInRepositoryOrder() {
        when(repository.queryForExport(List.of())).thenReturn(List.of(
                SubRoleEntity.builder().id("s2").name("B").key("kb").build(),
                SubRoleEntity.builder().id("s1").name("A").key("ka").build()));
        when(repository.queryUsernamesBySubRoleId("s2")).thenReturn(List.of("u1"));
        when(repository.queryRoleKeysBySubRoleId("s2")).thenReturn(List.of("r1"));
        when(repository.queryUsernamesBySubRoleId("s1")).thenReturn(List.of());
        when(repository.queryRoleKeysBySubRoleId("s1")).thenReturn(List.of());

        var rows = service().queryExportRows(List.of());

        assertEquals(2, rows.size());
        assertEquals("B", rows.get(0).getName());
        assertEquals(List.of("u1"), rows.get(0).getUsernames());
        assertEquals(List.of("r1"), rows.get(0).getRoleKeys());
        assertEquals(List.of(), rows.get(1).getUsernames());
    }

    @Test
    void idsArePassedThroughAndNameOverlaysAreNull() {
        when(repository.queryForExport(List.of("s1"))).thenReturn(List.of(
                SubRoleEntity.builder().id("s1").name("A").key("ka").desc("d").build()));
        when(repository.queryUsernamesBySubRoleId("s1")).thenReturn(List.of("u1", "u2"));
        when(repository.queryRoleKeysBySubRoleId("s1")).thenReturn(List.of("r1"));

        var rows = service().queryExportRows(List.of("s1"));

        verify(repository).queryForExport(List.of("s1"));
        assertEquals("d", rows.get(0).getDesc());
        assertEquals(List.of("u1", "u2"), rows.get(0).getUsernames());
        assertNull(rows.get(0).getNameUs(), "i18n 覆盖本环境无数据 ⇒ null");
        assertNull(rows.get(0).getNameFr());
    }
}
