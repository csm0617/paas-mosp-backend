package com.csm.paas.core.domain.backup.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 备份域的通用分页信封 `{list,total}`（厂商 `DatabaseBackupList` / `BackupFileList` /
 * `BackupSettingInfo` / `BackupRecoveryLogList` 四者是同形类）。
 *
 * <p>`total` 是**基本类型 long**，照抄厂商。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupPageEntity<T> {

    private List<T> list;
    private long total;
}
