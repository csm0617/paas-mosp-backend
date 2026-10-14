package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务器上的备份文件条目（厂商 {@code controller/response/BackupFileInfo.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/BackupFileInfo.java:8-11`。
 * <p>照抄点：`key` 只有三个取值——`gaop`（baseBackupInfo.zip）、`database`（dataBase_backup.zip）、
 * `image`（`*.tar.gz`），见厂商 `BackService.getBackFileList:1491-1526`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupFileInfoDTO {

    private String key;
    private String name;
}
