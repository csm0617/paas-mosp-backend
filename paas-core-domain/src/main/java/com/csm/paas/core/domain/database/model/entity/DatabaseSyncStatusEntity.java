package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入推送状态（厂商 {@code controller/websocket/model/DbImportStatus}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.importStatus`（静态单例），
 * 由 `GET /v1/database/syncStatus` 返回（`DatabaseService.syncDatabaseInfo:1472-1474`）。
 * <p>照抄点：初始值是 `DbImportStatus.init()` = `{syncing:false, success:false, current:0}`，
 * 其余字段为默认值（`total=0`、`currentDatabaseName=null`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseSyncStatusEntity {

    private boolean syncing;
    private boolean success;
    private String currentDatabaseName;
    private int current;
    private int total;

    /** 照抄厂商 `DbImportStatus.init()`。 */
    public static DatabaseSyncStatusEntity init() {
        return DatabaseSyncStatusEntity.builder().syncing(false).success(false).current(0).build();
    }
}
