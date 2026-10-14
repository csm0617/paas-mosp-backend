package com.csm.paas.core.domain.sync.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `SyncStatus`（`GET /v1/sync/status` 的响应体）：
 * `{syncing, success, currentSyncName, currentSyncType, current, total, errorStatus}`。
 *
 * <p>厂商是**一个 static 单例**（`SyncController.syncStatus = SyncStatus.init()`），
 * 初始值 = `{syncing:false, success:false, current:0, errorStatus:null}`（其余字段为类型默认值）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncStatusEntity {

    private boolean syncing;
    private boolean success;
    private String currentSyncName;
    private String currentSyncType;
    private int current;
    private int total;
    private String errorStatus;

    /** 厂商 `SyncStatus.init()`：四个字段显式给值，其余取类型默认。 */
    public static SyncStatusEntity init() {
        return SyncStatusEntity.builder()
                .syncing(false).success(false).current(0).errorStatus(null)
                .build();
    }
}
