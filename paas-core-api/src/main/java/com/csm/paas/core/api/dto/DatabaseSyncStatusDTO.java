package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库导入推送状态（厂商 {@code controller/websocket/model/DbImportStatus.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/websocket/model/DbImportStatus.java:6-18`。
 * <p>照抄点：`GET /v1/database/syncStatus` 返回的是**进程内静态单例** `DatabaseService.importStatus`
 * （初始 `{syncing:false,success:false,current:0,total:0,currentDatabaseName:null}`），
 * 同时它也被推给 STOMP `/topic/database`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseSyncStatusDTO {

    private boolean syncing;
    private boolean success;
    private String currentDatabaseName;
    private int current;
    private int total;
}
