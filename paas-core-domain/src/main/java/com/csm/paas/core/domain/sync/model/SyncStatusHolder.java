package com.csm.paas.core.domain.sync.model;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;

/**
 * 同步状态单例（厂商 `SyncController.syncStatus = SyncStatus.init()`，
 * `SyncController.java:151` 的 **static** 字段）。
 *
 * <p>厂商 `GET /status` 与 `POST /v1/sync` 读写**同一个对象**；所以这里也用一个静态持有者，
 * 让 `SyncQueryServiceImpl`（读）与 `SyncCommandServiceImpl`（写）共享。
 */
public final class SyncStatusHolder {

    /** 进程内单例（照抄厂商的 static 语义）。 */
    public static final SyncStatusEntity INSTANCE = SyncStatusEntity.init();

    private SyncStatusHolder() {
    }
}
