package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务器上的备份文件条目（厂商 {@code controller/response/BackupFileInfo.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../BackService.getBackFileList:1491-1526`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupFileInfoEntity {

    private String key;
    private String name;
}
