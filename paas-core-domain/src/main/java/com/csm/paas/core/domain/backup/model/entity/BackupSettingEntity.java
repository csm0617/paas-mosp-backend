package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_backup_setting` 的一行（厂商 {@code model/BackupSetting.java:12-24}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/BackupSetting.java`。
 * `type`：0=仅表结构（mysqldump `--no-data`）、1=表结构与数据，见
 * `DatabaseService.getFileListByBackUpSetting:2298-2335`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupSettingEntity {

    private Long id;
    private String databaseName;
    private Integer type;
}
