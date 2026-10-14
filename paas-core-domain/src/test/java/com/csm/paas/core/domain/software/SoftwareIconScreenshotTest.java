package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.SoftwareCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/software/update_icon` 与 `update_screenshot`
 * （厂商 `SoftwareService.updateIcon:2577-2595` / `updateScreenshot:2556-2575`）。
 * <p>规格见 `docs/findings/130`。两处都是三分支：**与库里相同 ⇒ 不写库**、不同 ⇒ 写 + 推进 `updated_at`、
 * **null/空 ⇒ 清空**；软件不存在 ⇒ 400 `software.not.found`（不是 `software.not.exist.by.id`）。
 */
class SoftwareIconScreenshotTest {

    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private SoftwareCommandServiceImpl service() {
        return new SoftwareCommandServiceImpl(softwareRepository);
    }

    private void existing(String icon, String screenshot) {
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(
                SoftwareEntity.builder().id("sw1").icon(icon).screenshot(screenshot).build()));
    }

    // ---------- update_icon ----------

    @Test
    void sameIconIsNotWritten() {
        existing("data:image/png;base64,AAA", null);

        assertEquals("data:image/png;base64,AAA", service().updateIcon("sw1", "data:image/png;base64,AAA"));
        verify(softwareRepository, never()).updateSoftwareIcon(anyString(), anyString(), anyLong());
    }

    @Test
    void differentIconIsWritten() {
        existing("old", null);

        assertEquals("new", service().updateIcon("sw1", "new"));
        verify(softwareRepository).updateSoftwareIcon(eq("sw1"), eq("new"), anyLong());
    }

    @Test
    void nullIconClearsTheColumn() {
        existing("old", null);

        assertNull(service().updateIcon("sw1", null));
        verify(softwareRepository).updateSoftwareIcon(eq("sw1"), isNull(), anyLong());
    }

    @Test
    void iconOnMissingSoftwareYieldsSoftwareNotFound() {
        when(softwareRepository.queryById("nope")).thenReturn(Optional.empty());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateIcon("nope", "x"));
        assertEquals("software.not.found", e.getI18nKey());
        verify(softwareRepository, never()).updateSoftwareIcon(anyString(), anyString(), anyLong());
    }

    // ---------- update_screenshot ----------

    @Test
    void screenshotsAreJoinedByComma() {
        existing(null, null);

        assertEquals(List.of("a.png", "b.png"), service().updateScreenshot("sw1", List.of("a.png", "b.png")));
        verify(softwareRepository).updateSoftwareScreenshot(eq("sw1"), eq("a.png,b.png"), anyLong());
    }

    @Test
    void sameScreenshotListIsNotWritten() {
        existing(null, "a.png,b.png");

        assertEquals(List.of("a.png", "b.png"), service().updateScreenshot("sw1", List.of("a.png", "b.png")));
        verify(softwareRepository, never()).updateSoftwareScreenshot(anyString(), anyString(), anyLong());
    }

    @Test
    void emptyOrNullListClearsTheColumn() {
        existing(null, "a.png");

        assertEquals(List.of(), service().updateScreenshot("sw1", List.of()));
        verify(softwareRepository).updateSoftwareScreenshot(eq("sw1"), isNull(), anyLong());

        // 厂商对 null 会 NPE ⇒ 500；我们按空列表处理（B21）
        assertEquals(List.of(), service().updateScreenshot("sw1", null));
        verify(softwareRepository, Mockito.times(2)).updateSoftwareScreenshot(eq("sw1"), isNull(), anyLong());
    }

    @Test
    void screenshotOnMissingSoftwareYieldsSoftwareNotFound() {
        when(softwareRepository.queryById("nope")).thenReturn(Optional.empty());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateScreenshot("nope", List.of("a")));
        assertEquals("software.not.found", e.getI18nKey());
    }
}
