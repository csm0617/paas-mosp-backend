package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备份/还原日志（厂商 {@code controller/websocket/model/BackupRecoveryLog.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/BackupLogService.java` 的静态列表，
 * 由 `GET /v1/backup/backupLogs`（type=1）与 `GET /v1/backup/recoveryLogs`（type=2）读取。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupRecoveryLogEntity {

    private String type;
    private String module;
    private String message;
    private String data;
    private long timestamp;
}
