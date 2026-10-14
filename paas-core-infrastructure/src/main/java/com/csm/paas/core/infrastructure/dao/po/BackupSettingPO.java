package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * `g_backup_setting` 行（厂商 `model/BackupSetting`，列名见
 * `dao/BackupSettingDynamicSqlSupport.java:18-20`）。
 *
 * <p>列：`id, database_name, type`。
 */
@Data
public class BackupSettingPO {

    private Long id;
    private String databaseName;
    private Integer type;
}
