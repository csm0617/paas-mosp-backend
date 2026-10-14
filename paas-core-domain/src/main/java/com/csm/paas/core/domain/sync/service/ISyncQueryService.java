package com.csm.paas.core.domain.sync.service;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;

/**
 * 数据同步查询（厂商 `SyncController` 的 `GET /v1/sync/status`）。
 *
 * <p>状态是一个**进程内单例**（{@code SyncStatusHolder.INSTANCE}，对应厂商的 static
 * `SyncController.syncStatus`）；`POST /v1/sync`（{@code ISyncCommandService}）读写同一个对象，
 * 所以同步中 `GET /status` 能看到进度。注意厂商在线程末尾**推完就复位**，
 * 事后看到的仍是初始形状。
 */
public interface ISyncQueryService {

    /**
     * 当前同步状态（厂商 `SyncController:252-255` 直接返回 static 单例）。
     *
     * <p>初始形状（厂商 `SyncStatus.init()`）：
     * `{syncing:false, success:false, currentSyncName:null, currentSyncType:null, current:0, total:0, errorStatus:null}`。
     */
    SyncStatusEntity queryStatus();
}
