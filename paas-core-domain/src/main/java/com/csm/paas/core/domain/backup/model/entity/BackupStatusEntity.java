package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一键备份 / 镜像备份进度（厂商 {@code controller/websocket/model/BackStatus.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../BackService.backupInfoToJob:393-399` 与
 * `backupInfo:455-620`：`status` 0=空闲 1=进行中 2=完成 3=失败/中断；
 * `currentType` 在两段之间从 `gaop` 变成 `database`、再变成 `image`；
 * `BackService` 里同时有 `backupStatus`（`/topic/backup`）与 `imageStatus`（`/topic/serviceImage`）两个静态实例。
 *
 * <p><b>本次实现差异</b>：厂商把这两个对象推给 STOMP（`SimpMessageSendingOperations`）。
 * 本仓 domain 不依赖 spring-messaging，且无法在文件白名单内新增 trigger 广播器 ⇒
 * 这里只保留**状态机本身**，进度推送（`/topic/backup`、`/topic/serviceImage`、`/topic/saveDatabase`、
 * `/topic/database`）本次**未接**（见回报的未做项）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupStatusEntity {

    private int status;
    private String currentType;
    private int current;
    private int total;
    private String currentBackupName;

    /** 照抄厂商 `BackStatus.init()` 的语义：全零 + `currentType=null`。 */
    public static BackupStatusEntity init() {
        return BackupStatusEntity.builder().status(0).current(0).total(0).build();
    }
}
