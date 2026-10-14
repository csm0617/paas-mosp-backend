package com.csm.paas.core.domain.backup.service;

import com.csm.paas.core.domain.backup.model.entity.BackupRecoveryLogEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 备份/还原日志的**进程内静态列表**（厂商 `service/BackupLogService`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/BackupLogService.java`——
 * `public static List&lt;BackupRecoveryLog&gt; backLogs` 与 `recoveryLogs` 两个静态字段，
 * 由 `sendBackupLog` / `sendRecoveryLog` 追加、由
 * `BackService.getBackupLogs(page,size,type)`（type=1 取 backLogs、type=2 取 recoveryLogs）读取。
 * 本类照抄"进程内静态、不落库、重启即空"的语义。
 */
public final class BackupLogHolder {

    /** 厂商 `BackupLogService.backLogs`。 */
    public static final List<BackupRecoveryLogEntity> BACK_LOGS = Collections.synchronizedList(new ArrayList<>());

    /** 厂商 `BackupLogService.recoveryLogs`。 */
    public static final List<BackupRecoveryLogEntity> RECOVERY_LOGS =
            Collections.synchronizedList(new ArrayList<>());

    private BackupLogHolder() {
    }

    /**
     * 照抄厂商 `sendBackupLog(module, message, data, type)`：
     * `data` 直接 `String.valueOf`（对象会变成 `toString()`，厂商对 `BackStatus`/`ImageInfo` 也是这样）。
     */
    public static void sendBackupLog(String module, String message, Object data, String type) {
        BACK_LOGS.add(BackupRecoveryLogEntity.builder()
                .type(type).module(module).message(message)
                .data(data == null ? null : String.valueOf(data))
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /** 照抄厂商 `sendRecoveryLog`。 */
    public static void sendRecoveryLog(String module, String message, String data, String type) {
        RECOVERY_LOGS.add(BackupRecoveryLogEntity.builder()
                .type(type).module(module).message(message).data(data)
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
