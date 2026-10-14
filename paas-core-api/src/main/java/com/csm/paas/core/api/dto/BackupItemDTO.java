package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 {@code model/Backup}（表 `g_backup`）的响应形状。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/Backup.java:12-24`——7 个字段，全部原样输出。
 * <p>照抄点：`databaseKey` 是内部标识（user/role/sub_role/software/ingress/configmap/image/database），
 * `databaseName` 在列表接口里会被 i18n 文案**覆盖**（厂商 `BackService.i18n:196-205` 用
 * `backup.list.name.<key>` 覆盖 `databaseName`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupItemDTO {

    private Long id;
    private String databaseKey;
    private String databaseName;
    private String databaseFilename;
    private Long size;
    private Long createdAt;
    private Long updatedAt;
    private String msg;
}
