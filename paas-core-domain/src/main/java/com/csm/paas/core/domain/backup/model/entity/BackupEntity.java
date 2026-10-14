package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_backup` 的一行（厂商 {@code model/Backup.java:12-24}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/Backup.java`。八个字段逐个照抄；
 * 注意 `databaseKey`（标识）与 `databaseName`（展示名）是两个字段——厂商在列表接口里
 * 用 i18n 文案**覆盖** `databaseName`（`BackService.i18n:196-205`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupEntity {

    private Long id;
    private String databaseKey;
    private String databaseName;
    private String databaseFilename;
    private Long size;
    private Long createdAt;
    private Long updatedAt;
    private String msg;
}
