package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务数据库备份设置的一行（厂商 {@code model/BackupSetting}，表 `g_backup_setting`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/BackupSetting.java:12-24`。
 * <p>`type` 的语义（厂商 `BackController.getBackupSettingList` 的 `@Parameter` 文案）：
 * 0=仅数据结构（mysqldump `--no-data`）、1=数据结构与数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupSettingItemDTO {

    private Long id;
    private String databaseName;
    private Integer type;
}
