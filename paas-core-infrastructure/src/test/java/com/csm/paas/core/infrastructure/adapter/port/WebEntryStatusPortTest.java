package com.csm.paas.core.infrastructure.adapter.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * web 入口状态码的映射（离线）。**逐行照抄厂商** `SoftwareService.setServiceStatusBySoftware`（`:564-585`），
 * 包括两处古怪：
 * <ol>
 *   <li>内层分支里先 `setWeb(3)` 又**无条件** `setWeb(1)`，所以那个 3 永远被覆盖；</li>
 *   <li>`availableReplicas` 非空但其余有一项为 null 时，落到链尾 → 2。</li>
 * </ol>
 * 实测对拉（2026-10-14）：无入口行 → 0；入口行的 Deployment 找不到 → 2；找到且健康 → 1。
 */
class WebEntryStatusPortTest {

    @Test
    void allReplicaFieldsPresentYieldsOneEvenWhenReadyExceedsAvailable() {
        // 厂商在这里会先设 3（unavailable != null && ready > available），紧接着被无条件设成 1
        assertEquals(1, WebEntryStatusPort.toWeb(2, 3, 3, 3, 1));
    }

    @Test
    void availableNullAndOthersPresentYieldsOne() {
        assertEquals(1, WebEntryStatusPort.toWeb(null, 1, 1, 1, 1));
    }

    @Test
    void availableNullAndReadyMissingYieldsThree() {
        // available==null → 走第二条链；ready 缺失 → 落到第三条（replicas/updated/unavailable 都非空）→ 3
        assertEquals(3, WebEntryStatusPort.toWeb(null, null, 1, 1, 1));
    }

    @Test
    void availableNullAndUnavailableMissingFallsThroughToTwo() {
        assertEquals(2, WebEntryStatusPort.toWeb(null, 1, 1, 1, null));
    }

    @Test
    void availablePresentButOthersMissingYieldsTwo() {
        assertEquals(2, WebEntryStatusPort.toWeb(1, null, null, null, null));
    }

    @Test
    void allNullYieldsTwo() {
        assertEquals(2, WebEntryStatusPort.toWeb(null, null, null, null, null));
    }
}
