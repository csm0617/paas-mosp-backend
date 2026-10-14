package com.csm.paas.core.domain.language;

import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity;
import com.csm.paas.core.domain.language.service.impl.LanguageCommandServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `DELETE /v1/lang/remove`（厂商 `LanguageServiceImpl.deleteLangVersion:349-362`）。
 * <p>规格见 `docs/findings/138`。重点：`lang` 归一化、**`default` 版本不许删**、
 * **不存在 ⇒ true（幂等）**、先删词条再删版本行、返回值是"版本行是否真删掉"。
 */
class LanguageDeleteVersionTest {

    private final ILanguageVersionRepository repository = mock(ILanguageVersionRepository.class);
    private final com.csm.paas.core.domain.language.adapter.port.ILanguageI18nDirectoryPort i18nDirectory =
            mock(com.csm.paas.core.domain.language.adapter.port.ILanguageI18nDirectoryPort.class);

    private LanguageCommandServiceImpl service() {
        return new LanguageCommandServiceImpl(repository, i18nDirectory);
    }

    @Test
    void defaultVersionIsNeverDeleted() {
        assertFalse(service().deleteLanguageVersion("zh_CN", "default"));
        verify(repository, never()).deleteVersionById(anyLong());
        verify(repository, never()).deleteLanguageEntries(anyString(), anyString());
    }

    @Test
    void langIsNormalizedBeforeLookup() {
        when(repository.queryByLangAndVersion("en_US", "v2"))
                .thenReturn(Optional.of(LanguageVersionEntity.builder()
                        .id(9L).lang("en_US").langVersion("v2").build()));
        when(repository.deleteVersionById(9L)).thenReturn(1);

        assertTrue(service().deleteLanguageVersion("en-US", "v2"));

        verify(repository).queryByLangAndVersion("en_US", "v2");
    }

    @Test
    void missingVersionIsIdempotentTrue() {
        when(repository.queryByLangAndVersion("en_US", "v9")).thenReturn(Optional.empty());

        assertTrue(service().deleteLanguageVersion("en_US", "v9"));

        verify(repository, never()).deleteVersionById(anyLong());
        verify(repository, never()).deleteLanguageEntries(anyString(), anyString());
    }

    @Test
    void entriesAreDeletedBeforeTheVersionRow() {
        when(repository.queryByLangAndVersion("en_US", "v2"))
                .thenReturn(Optional.of(LanguageVersionEntity.builder()
                        .id(9L).lang("en_US").langVersion("v2").build()));
        when(repository.deleteVersionById(9L)).thenReturn(1);

        service().deleteLanguageVersion("en_US", "v2");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).queryByLangAndVersion("en_US", "v2");
        order.verify(repository).deleteLanguageEntries("en_US", "v2");
        order.verify(repository).deleteVersionById(9L);
    }

    @Test
    void returnValueFollowsTheRowCount() {
        when(repository.queryByLangAndVersion("en_US", "v2"))
                .thenReturn(Optional.of(LanguageVersionEntity.builder()
                        .id(9L).lang("en_US").langVersion("v2").build()));
        when(repository.deleteVersionById(9L)).thenReturn(0);

        assertFalse(service().deleteLanguageVersion("en_US", "v2"), "厂商返回 delete > 0");
    }
}
