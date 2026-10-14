package com.csm.paas.core.domain.backup.service;

import com.csm.paas.core.domain.backup.model.entity.BackupStatusEntity;

/**
 * 一键备份/镜像备份的**进程内静态状态**（厂商 `BackService.backupStatus` / `imageStatus`
 * 与 `controller/Job/BackUpJob.flag`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/BackService.java:130-131`：
 * `public static BackStatus backupStatus = BackStatus.init();`、`public static BackStatus imageStatus = ...`；
 * `controller/Job/BackUpJob.java` 的 `public static Boolean flag`（默认 false，`POST /v1/backup` 入口置 false，
 * 取消备份时置 true 用作"中断标识"）。
 */
public final class BackupStatusHolder {

    /** 厂商 `BackService.backupStatus`（`/topic/backup`）。 */
    public static volatile BackupStatusEntity backupStatus = BackupStatusEntity.init();

    /** 厂商 `BackService.imageStatus`（`/topic/serviceImage`）。 */
    public static volatile BackupStatusEntity imageStatus = BackupStatusEntity.init();

    /** 厂商 `BackUpJob.flag`：true = 请求中断。 */
    public static volatile boolean jobInterrupted = false;

    private BackupStatusHolder() {
    }
}
