package com.csm.paas.core.domain.backup.adapter.repository;

import com.csm.paas.core.domain.backup.model.entity.BackupEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupSettingEntity;

import java.util.List;
import java.util.Optional;

/**
 * 备份域本地仓储（`g_backup` + `g_backup_setting`）。实现放 infrastructure，只能调 dao。
 *
 * <p>厂商出处：
 * <ul>
 *   <li>`g_backup`：`BackService.getDatabaseBackupList:180-194`（`database_name` 模糊、`created_at DESC`）、
 *       `getBackupInfo:635-645`（按 `database_key` 取第 0 条后整行 update）、
 *       `updateBackUpByError:623-633`（按 key 选择性 update）、
 *       `getBackupFileStatus:1616-1709` / `downloadSingleBackup:1228-1334`（按 key 取第 0 条）</li>
 *   <li>`g_backup_setting`：`getBackupSettingList:1557-1600`、`changeBackupSetting:1602-1614`</li>
 * </ul>
 * <p>分页照抄厂商的 PageHelper 语义（MySQL `LIMIT/OFFSET`；厂商 `total` 是**过滤后的总行数**）。
 */
public interface IBackupRepository {

    /** 按 `database_name` 模糊（null=不过滤）分页，`created_at DESC`。 */
    List<BackupEntity> queryBackupPage(String likeKeywordOrNull, int offset, int size);

    /** 同上的总行数（PageHelper 的 `total`）。 */
    long countBackups(String likeKeywordOrNull);

    /** 按 `database_key` 取第 0 条（厂商到处用 `.get(0)`；取不到返回 empty，由调用方决定语义）。 */
    Optional<BackupEntity> queryByKey(String databaseKey);

    /** 整行 update（厂商 `updateByPrimaryKey`，null 字段**会**被写回）。 */
    void updateByPrimaryKey(BackupEntity backup);

    /** 选择性 update（厂商 `updateByPrimaryKeySelective`，null 字段跳过）。 */
    void updateByPrimaryKeySelective(BackupEntity backup);

    /** `g_backup_setting` 全量，无排序（厂商 `SelectDSLCompleter.allRows()`）。 */
    List<BackupSettingEntity> queryAllSettings();

    /** `g_backup_setting` 分页（厂商先 startPage 再 allRows）。 */
    List<BackupSettingEntity> querySettingPage(int offset, int size);

    /** `g_backup_setting` 总行数。 */
    long countSettings();

    Optional<BackupSettingEntity> querySettingByName(String databaseName);

    void insertSetting(String databaseName, Integer type);

    void deleteSetting(String databaseName);

    /** 厂商 `changeBackupSetting` 返回 `updateByPrimaryKeySelective(setting) > 0`。 */
    boolean updateSettingType(String databaseName, Integer type);

    void deleteAllSettings();
}
