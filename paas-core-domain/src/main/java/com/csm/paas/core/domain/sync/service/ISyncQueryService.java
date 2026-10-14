package com.csm.paas.core.domain.sync.service;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;

/**
 * 数据同步查询（厂商 `SyncController` 的 `GET /v1/sync/status` 等）。
 *
 * <p>**未实现**：`POST /v1/sync`（真正的同步，依赖 SSH/rsync 与对端节点）与
 * `GET /v1/sync/database/exist`（要打对端节点的 HTTP 接口）。
 * 因此这里的 `SyncStatusEntity` **始终是初始状态**（`syncing=false`），
 * 与厂商"没有同步在跑时"的可观测结果一致。
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
