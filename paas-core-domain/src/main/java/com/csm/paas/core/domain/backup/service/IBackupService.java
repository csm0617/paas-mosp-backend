package com.csm.paas.core.domain.backup.service;

import com.csm.paas.core.domain.backup.model.entity.BackupDownloadEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupFileInfoEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupFileStatusEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupPageEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupRecoveryLogEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupSettingEntity;

import java.io.IOException;
import java.io.InputStream;

/**
 * `/v1/backup` 的业务服务（厂商 {@code service/BackService.java}，1911 行）。
 *
 * <p>行号都是厂商出处。所有怪点照抄，见实现类 javadoc。
 */
public interface IBackupService {

    // ---------------------------------------------------------------- 读

    /** 厂商 `getDatabaseBackupList:180-194`：`database_name` 模糊 + `created_at DESC` 分页（**不做 i18n**，i18n 在 trigger 层）。 */
    BackupPageEntity<BackupEntity> queryBackupList(Integer page, Integer size, String keyword);

    /** 厂商 `getBackFileList:1491-1526`：扫备份目录，顺序 = gaop → database → 所有 `*.tar.gz`。 */
    BackupPageEntity<BackupFileInfoEntity> queryBackFileList(Integer page, Integer size);

    /** 厂商 `getBackupSettingList:1557-1600`：先用 MySQL 库列表对齐 `g_backup_setting`，再分页返回。 */
    BackupPageEntity<BackupSettingEntity> queryBackupSettings(Integer page, Integer size);

    /** 厂商 `changeBackupSetting:1602-1614`：库名不存在 → 400 `database.name.not.found`。 */
    boolean changeBackupSetting(String databaseName, Integer type);

    /** 厂商 `getBackupFileStatus:1616-1709`。 */
    BackupFileStatusEntity queryBackupFileStatus(String key, String name);

    /**
     * 厂商 `getBackupLogs:1892-1910`。
     *
     * @param type 1=备份日志（`BackupLogService.backLogs`）、2=还原日志（`recoveryLogs`）；
     *             其他值厂商会 NPE（switch 无 default）
     */
    BackupPageEntity<BackupRecoveryLogEntity> queryLogs(Integer page, Integer size, int type);

    /**
     * 厂商 `downloadSingleBackup:1228-1334`：选文件 + 解析 Range。
     *
     * <p>**照抄点**：未知 `dataKey` 时厂商**什么都不做**（方法直接走到结尾），所以这里返回 {@code null}，
     * 由 controller 保持"200 + 空 body"。
     */
    BackupDownloadEntity prepareSingleDownload(String range, String dataKey, String fileName);

    /** 备份目录（厂商 `BackService.getFileBackUp:1888-1890`，配置项 `data.backup`）。 */
    String backupDir();

    // ---------------------------------------------------------------- 写

    /** 厂商 `backUpBaseInfo:271-381`（`POST /v1/backup/base`）：把 6 份基础数据压成 `baseBackupInfo.zip`。 */
    boolean backUpBaseInfo() throws IOException;

    /** 厂商 `backupInfoToJob:383-406`（`POST /v1/backup`）：已存在备份任务 → 400 `Backup job already exists.`。 */
    boolean backupInfoToJob();

    /** 厂商 `cancelBackupJob:408-424`（`POST /v1/backup/delete`）。 */
    boolean cancelBackupJob();

    /** 厂商 `backupInfo:449-621`（Quartz 任务体，`@Async`）。 */
    boolean backupInfo();

    /** 厂商 `singleBackupByBase:1066-1091`（`POST /v1/backup/singleBaseData`）。 */
    boolean singleBackupByBase(String dataKey) throws IOException;

    /** 厂商 `singleBackupByService:1054-1064`（`POST /v1/backup/singleService`）。 */
    boolean singleBackupByService(String dataKey) throws IOException;

    /** 厂商 `resetAll:1711-1721`（`POST /v1/backup/reset`）。 */
    boolean resetAll();

    /** 厂商 `resetByKey:1723-1766`：未知 key → 400 `reset.is.unknown.key`。 */
    boolean resetByKey(String key);

    /** 厂商 `downloadBaseInfo:1389-1489`（`GET /v1/backup/baseData`）：没有 zip 就现做一份。 */
    InputStream downloadBaseInfo() throws IOException;
}
