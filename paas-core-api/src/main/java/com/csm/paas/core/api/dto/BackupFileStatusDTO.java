package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备份文件信息（厂商 {@code controller/response/BackupFileStatus.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/BackupFileStatus.java:6-12`。
 * <p>照抄点：三个字段都可能为 **null**（文件不存在时 `size`/`md5` 置 null）；`fileName` 只有
 * `key=="image"` 时才等于入参 `name`，`key=="database"` 时取**库里 `g_backup` 的 `database_filename`**——
 * 入参 `name` 被忽略（厂商 `BackService.getBackupFileStatus:1616-1709`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupFileStatusDTO {

    private String fileName;
    private Long size;
    private String md5;
}
