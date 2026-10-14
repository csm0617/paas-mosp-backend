package com.csm.paas.core.domain.language;

import com.csm.paas.core.domain.language.adapter.port.ILanguageI18nDirectoryPort;
import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.service.impl.LanguageCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/lang/version`（厂商 `LanguageServiceImpl.setLanguageVersion:421-437`）。
 * <p>规格见 `docs/findings/140`。重点：**先清全局 used=0 再置 1**（两步都发生）、
 * `default` 分支的"非基础语言 ⇒ false / `./i18n` 存在则删"、以及"版本不存在 ⇒ false 且**不写库**"。
 */
class LanguageSetVersionTest {

    private final ILanguageVersionRepository repository = mock(ILanguageVersionRepository.class);
    private final ILanguageI18nDirectoryPort i18nDirectory = mock(ILanguageI18nDirectoryPort.class);

    private LanguageCommandServiceImpl service() {
        return new LanguageCommandServiceImpl(repository, i18nDirectory);
    }

    @Test
    void nullLangOrVersionIsFalse() {
        assertFalse(service().setLanguageVersion(null, "v1"));
        assertFalse(service().setLanguageVersion("zh_CN", null));
        verify(repository, never()).resetUsed();
    }

    @Test
    void normalVersionResetsThenSetsAndReturnsRowCount() {
        when(repository.countByLangAndVersion("en_US", "v2")).thenReturn(1L);
        when(repository.setUsed("en_US", "v2")).thenReturn(1);

        assertTrue(service().setLanguageVersion("en-US", "v2"));

        org.mockito.InOrder order = Mockito.inOrder(repository);
        order.verify(repository).resetUsed();
        order.verify(repository).setUsed("en_US", "v2");
    }

    @Test
    void unknownVersionIsFalseAndTouchesNothing() {
        when(repository.countByLangAndVersion("en_US", "v9")).thenReturn(0L);

        assertFalse(service().setLanguageVersion("en_US", "v9"));

        verify(repository, never()).resetUsed();
        verify(repository, never()).setUsed(anyString(), anyString());
    }

    @Test
    void setUsedReturningZeroIsFalse() {
        when(repository.countByLangAndVersion("en_US", "v2")).thenReturn(1L);
        when(repository.setUsed("en_US", "v2")).thenReturn(0);

        assertFalse(service().setLanguageVersion("en_US", "v2"));
    }

    @Test
    void defaultVersionDeletesI18nDirectoryWhenPresent() {
        when(i18nDirectory.exists()).thenReturn(true);
        when(repository.setUsed("zh_CN", "default")).thenReturn(1);

        assertTrue(service().setLanguageVersion("zh-CN", "default"));

        verify(repository).resetUsed();
        verify(repository).setUsed("zh_CN", "default");
        verify(i18nDirectory).delete();
    }

    @Test
    void defaultVersionWithoutDirectoryStillTrue() {
        when(i18nDirectory.exists()).thenReturn(false);
        when(repository.setUsed("zh_CN", "default")).thenReturn(1);

        assertTrue(service().setLanguageVersion("zh_CN", "default"));

        verify(i18nDirectory, never()).delete();
    }

    @Test
    void defaultVersionForNonBaseLanguageIsFalseButStillWritesUsedFlags() {
        // 厂商：先 updateLanguageVersion(...) 再 setDefaultVersionLang（后者对非基础语言返回 false）
        when(repository.setUsed("de_DE", "default")).thenReturn(1);

        assertFalse(service().setLanguageVersion("de_DE", "default"));

        verify(repository).resetUsed();
        verify(repository).setUsed("de_DE", "default");
        verify(i18nDirectory, never()).exists();
    }

    @Test
    void directoryDeleteFailurePropagatesAsWrongArgument() {
        when(i18nDirectory.exists()).thenReturn(true);
        when(repository.setUsed("zh_CN", "default")).thenReturn(1);
        Mockito.doThrow(new WrongArgumentException("globalization.file.delete.failed"))
                .when(i18nDirectory).delete();

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().setLanguageVersion("zh_CN", "default"));
        assertTrue("globalization.file.delete.failed".equals(e.getI18nKey()));
    }
}
