package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 备份域的四种「列表信封」，形状完全一致：`{list,total}`。
 *
 * <p>厂商是四个各自的类，字段名与类型逐字相同，只是元素类型不同：
 * <ul>
 *   <li>{@code controller/response/DatabaseBackupList.java}：`List&lt;Backup&gt; list` + `long total`（`GET /v1/backup/list`）</li>
 *   <li>{@code controller/response/BackupFileList.java}：`List&lt;BackupFileInfo&gt; list` + `long total`（`GET /v1/backup/backFileList`）</li>
 *   <li>{@code controller/response/BackupSettingInfo.java}：`List&lt;BackupSetting&gt; list` + `long total`（`GET /v1/backup/backupSetting`）</li>
 *   <li>{@code controller/response/BackupRecoveryLogList.java}：`List&lt;BackupRecoveryLog&gt; list` + `long total`
 *       （`GET /v1/backup/backupLogs`、`GET /v1/backup/recoveryLogs`）</li>
 * </ul>
 * 序列化结果完全相同（`total` 是**基本类型 long**，照抄），故合并成一个泛型 DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupListDTO<T> {

    private List<T> list;
    private long total;
}
