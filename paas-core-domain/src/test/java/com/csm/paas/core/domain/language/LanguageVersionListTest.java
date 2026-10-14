package com.csm.paas.core.domain.language;

import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity;
import com.csm.paas.core.domain.language.service.impl.LanguageQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/lang/version`（厂商 `LanguageServiceImpl.getLangVersionList:267-281`）。
 * <p>规格见 `docs/findings/137`。重点：关键字**先去空格再 `-`→`_`**、空则不过滤、
 * 分页换算成 offset、`total` 用同一 where。
 */
class LanguageVersionListTest {

    private final ILanguageVersionRepository repository = mock(ILanguageVersionRepository.class);
    private final com.csm.paas.core.domain.language.adapter.port.ILanguagePropertiesPort propertiesPort =
            mock(com.csm.paas.core.domain.language.adapter.port.ILanguagePropertiesPort.class);

    private LanguageQueryServiceImpl service() {
        return new LanguageQueryServiceImpl(repository, propertiesPort);
    }

    private void page(String expectedLike, List<LanguageVersionEntity> rows, long total) {
        when(repository.queryLanguageVersionPage(expectedLike, 0, 20)).thenReturn(rows);
        when(repository.countLanguageVersions(expectedLike)).thenReturn(total);
    }

    @Test
    void noKeywordMeansNoFilter() {
        page(null, List.of(LanguageVersionEntity.builder().id(1L).lang("zh_CN").build()), 3);

        var result = service().queryLanguageVersionList(1, 20, null);

        assertEquals(1, result.getVersionList().size());
        assertEquals(3, result.getTotal());
        verify(repository).queryLanguageVersionPage(isNull(), eq(0), eq(20));
        verify(repository).countLanguageVersions(isNull());
    }

    @Test
    void keywordSpacesAreStrippedAndDashesBecomeUnderscores() {
        // " en - US " ⇒ 去空格 "en-US" ⇒ lang2Underline "en_US" ⇒ %en_US%
        when(repository.queryLanguageVersionPage("%en_US%", 0, 20)).thenReturn(List.of());
        when(repository.countLanguageVersions("%en_US%")).thenReturn(0L);

        service().queryLanguageVersionList(1, 20, " en - US ");

        verify(repository).queryLanguageVersionPage("%en_US%", 0, 20);
    }

    @Test
    void blankAfterStrippingMeansNoFilter() {
        when(repository.queryLanguageVersionPage(isNull(), eq(0), eq(20))).thenReturn(List.of());
        when(repository.countLanguageVersions(isNull())).thenReturn(0L);

        service().queryLanguageVersionList(1, 20, "   ");

        verify(repository).queryLanguageVersionPage(isNull(), eq(0), eq(20));
    }

    @Test
    void pageIsConvertedToOffset() {
        when(repository.queryLanguageVersionPage(isNull(), eq(40), eq(20))).thenReturn(List.of());
        when(repository.countLanguageVersions(isNull())).thenReturn(0L);

        service().queryLanguageVersionList(3, 20, null);

        verify(repository).queryLanguageVersionPage(isNull(), eq(40), eq(20));
    }

    @Test
    void rowsAndTotalComeFromTheSameWhere() {
        page("%zh%", List.of(
                LanguageVersionEntity.builder().id(1L).lang("zh_CN").langVersion("default").used(1).createTime(9L).build(),
                LanguageVersionEntity.builder().id(2L).lang("zh_TW").langVersion("v2").used(0).createTime(8L).build()), 7);

        var result = service().queryLanguageVersionList(1, 20, "zh");

        assertEquals(2, result.getVersionList().size());
        assertEquals(7, result.getTotal());
        assertEquals("zh_CN", result.getVersionList().get(0).getLang());
        assertEquals("v2", result.getVersionList().get(1).getLangVersion());
        verify(repository).countLanguageVersions("%zh%");
        verify(repository).queryLanguageVersionPage(eq("%zh%"), anyInt(), anyInt());
    }
}
