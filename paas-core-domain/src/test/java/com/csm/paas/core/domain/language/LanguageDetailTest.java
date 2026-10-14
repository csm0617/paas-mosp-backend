package com.csm.paas.core.domain.language;

import com.csm.paas.core.domain.language.adapter.port.ILanguagePropertiesPort;
import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;
import com.csm.paas.core.domain.language.model.entity.LanguageEntryEntity;
import com.csm.paas.core.domain.language.service.impl.LanguageQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/lang/detail`（厂商 `LanguageServiceImpl.getLangList:284-294`）。
 * <p>规格见 `docs/findings/139`。重点：`lang` 归一化、**version 为空取当前版本**、
 * `default` ⇒ 读内置 properties 并把 module 固定成 `gaop`、否则查 `g_language`（可选 module 过滤）、
 * `total` 是列表长度。
 */
class LanguageDetailTest {

    private final ILanguageVersionRepository repository = mock(ILanguageVersionRepository.class);
    private final ILanguagePropertiesPort propertiesPort = mock(ILanguagePropertiesPort.class);

    private LanguageQueryServiceImpl service() {
        return new LanguageQueryServiceImpl(repository, propertiesPort);
    }

    private void currentVersion(String version) {
        when(repository.queryCurrent()).thenReturn(Optional.of(
                CurrentLanguageEntity.builder().language("zh_CN").version(version).build()));
    }

    @Test
    void defaultVersionReadsBuiltinPropertiesAndForcesModule() {
        Map<String, String> builtin = new LinkedHashMap<>();
        builtin.put("a.b", "内容1");
        builtin.put("c.d", "内容2");
        when(propertiesPort.readBuiltinMessages("zh_CN")).thenReturn(builtin);

        var info = service().queryLanguageDetail("zh-CN", "default", null);

        assertEquals(2, info.getTotal());
        assertEquals("default", info.getLangVersion());
        assertEquals("zh_CN", info.getLang());
        assertEquals("gaop", info.getModule(), "default 分支的 module 固定回填 gaop");
        assertEquals("a.b", info.getLanguageList().get(0).getTextKey());
        assertEquals("内容1", info.getLanguageList().get(0).getContent());
        assertEquals("gaop", info.getLanguageList().get(0).getModule());
        verify(repository, never()).queryLanguages(anyString(), anyString(), anyString());
    }

    @Test
    void blankVersionFallsBackToCurrentVersion() {
        currentVersion("v9");
        when(repository.queryLanguages("en_US", "v9", "gaop")).thenReturn(List.of(
                LanguageEntryEntity.builder().id(1L).textKey("k").content("c").module("gaop").build()));

        var info = service().queryLanguageDetail("en_US", " ", "gaop");

        assertEquals("v9", info.getLangVersion());
        assertEquals(1, info.getTotal());
        verify(repository).queryLanguages("en_US", "v9", "gaop");
    }

    @Test
    void nullVersionAlsoFallsBackToCurrentVersion() {
        currentVersion("default");
        when(propertiesPort.readBuiltinMessages("fr_FR")).thenReturn(Map.of());

        var info = service().queryLanguageDetail("fr_FR", null, null);

        assertEquals("default", info.getLangVersion());
        assertEquals(0, info.getTotal());
    }

    @Test
    void moduleIsPassedThroughForDbBackedVersions() {
        when(repository.queryLanguages("zh_CN", "v2", "m1")).thenReturn(List.of(
                LanguageEntryEntity.builder().id(2L).textKey("k2").content("c2").module("m1").abbr("A").build()));

        var info = service().queryLanguageDetail("zh_CN", "v2", "m1");

        assertEquals("m1", info.getModule());
        assertEquals(1, info.getTotal());
        assertEquals("A", info.getLanguageList().get(0).getAbbr());
        verify(repository).queryLanguages("zh_CN", "v2", "m1");
    }

    @Test
    void totalIsTheListSize() {
        when(repository.queryLanguages("zh_CN", "v2", null)).thenReturn(List.of(
                LanguageEntryEntity.builder().id(1L).textKey("a").build(),
                LanguageEntryEntity.builder().id(2L).textKey("b").build(),
                LanguageEntryEntity.builder().id(3L).textKey("c").build()));

        var info = service().queryLanguageDetail("zh_CN", "v2", null);

        assertEquals(3, info.getTotal());
        assertEquals(3, info.getLanguageList().size());
    }
}
