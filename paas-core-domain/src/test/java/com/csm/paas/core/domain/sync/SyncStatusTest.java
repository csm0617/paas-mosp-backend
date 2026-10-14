package com.csm.paas.core.domain.sync;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;
import com.csm.paas.core.domain.sync.service.impl.SyncQueryServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * `GET /v1/sync/status`（厂商 `SyncController:151` 的 static `SyncStatus.init()` + `:252-255`）。
 * <p>规格见 `docs/findings/153`。
 */
class SyncStatusTest {

    @Test
    void initialShapeMatchesTheVendorStaticSingleton() {
        SyncStatusEntity status = new SyncQueryServiceImpl().queryStatus();

        assertFalse(status.isSyncing());
        assertFalse(status.isSuccess());
        assertNull(status.getCurrentSyncName());
        assertNull(status.getCurrentSyncType());
        assertEquals(0, status.getCurrent());
        assertEquals(0, status.getTotal());
        assertNull(status.getErrorStatus());
    }

    @Test
    void initExplicitlySetsTheFourFieldsTheVendorSets() {
        SyncStatusEntity status = SyncStatusEntity.init();

        assertFalse(status.isSyncing());
        assertFalse(status.isSuccess());
        assertEquals(0, status.getCurrent());
        assertNull(status.getErrorStatus());
    }

    @Test
    void sameInstanceIsReturnedEveryTime() {
        var service = new SyncQueryServiceImpl();
        assertSame(service.queryStatus(), service.queryStatus(), "厂商是一个 static 单例");
    }
}
