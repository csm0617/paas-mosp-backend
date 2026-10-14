package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/**
 * `g_backup` 行（厂商 `model/Backup`，列名见 `dao/BackupDynamicSqlSupport.java:23-30`）。
 *
 * <p>列：`id, database_key, database_name, database_filename, size, created_at, updated_at, msg`。
 * `map-underscore-to-camel-case: true` 已开，字段名与列名自动对上。
 */
@Data
public class BackupPO {

    private Long id;
    private String databaseKey;
    private String databaseName;
    private String databaseFilename;
    private Long size;
    private Long createdAt;
    private Long updatedAt;
    private String msg;
}
