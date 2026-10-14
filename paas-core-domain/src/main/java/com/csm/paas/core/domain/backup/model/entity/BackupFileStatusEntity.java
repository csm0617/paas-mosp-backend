package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备份文件信息（厂商 {@code controller/response/BackupFileStatus.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../BackService.getBackupFileStatus:1616-1709`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupFileStatusEntity {

    private String fileName;
    private Long size;
    private String md5;
}
