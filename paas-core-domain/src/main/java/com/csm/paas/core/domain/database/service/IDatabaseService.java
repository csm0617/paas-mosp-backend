package com.csm.paas.core.domain.database.service;

import com.csm.paas.core.domain.database.model.entity.DatabaseEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseExecResultEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseImportSqlInfoEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseListEntity;
import com.csm.paas.core.domain.database.model.entity.DatabasePermissionEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseSyncStatusEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserRelationInfoEntity;
import com.csm.paas.core.domain.database.model.entity.MysqlGlobalSettingsEntity;
import com.csm.paas.core.domain.database.model.valobj.DatabaseDescribeVO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * `/v1/database` 的业务服务（厂商 {@code service/DataBaseService.java}，类名是 `DatabaseService`）。
 *
 * <p>方法名后面的行号都是厂商出处，逐条照抄（含怪点）。
 * <p>本接口只保留 controller 直接调用的那一批；厂商那些只服务于「一键备份」内部流程的
 * 私有/包级方法（`zipDataBaseFile`、`getFileListByBackUpSetting`、`exportDatabaseUser`、
 * `importDatabaseUserRelationInfo` 等）按需保留为 public（因为 `BackService` 也调用了其中一部分）。
 */
public interface IDatabaseService {

    // ---------------------------------------------------------------- 读

    /** 厂商 `getUserTableCount:604-617`：MySQL 管理连接取「业务库+表数」，再用 `g_database` 补齐展示字段。 */
    DatabaseListEntity getUserTableCount(int page, int size, String keyword);

    /** 厂商 `getAccessControl:683-696`：库不存在 → 400 `database.not.found`；用户不存在 → 400 `user.not.found`。 */
    List<DatabasePermissionEntity> getAccessControl(String databaseName, String accessUser);

    /** 厂商 `getDatabaseBelongUsers:698-701`。 */
    List<String> getDatabaseBelongUsers(String databaseName);

    /** 厂商 `getAllMysqlGlobalSettings:841-870`。 */
    MysqlGlobalSettingsEntity getAllMysqlGlobalSettings();

    /** 厂商 `getBusinessDataBaseTableSize:2438-2462`。 */
    List<DatabaseTableSizeEntity> getBusinessDataBaseTableSize();

    /** 厂商 `getRelationDataBaseList:2378-2396`。 */
    List<DatabaseUserRelationInfoEntity> getRelationDataBaseList();

    /** 厂商 `getInitSqlName:2424-2432`：`g_database.sql_name`，取不到 null。 */
    String getInitSqlName(String databaseName);

    /** 厂商 `existDatabase:558-560`。 */
    boolean existDatabase(String databaseName);

    /** 厂商 `getDataBaseBackUp:2084-2092`：备份目录下的 `dataBase_backup.zip`，不存在 → 400 `database.no.db.snapshot.backup`。 */
    InputStream getDataBaseBackUp() throws IOException;

    /** 厂商 `getSnapShotStatus:889-920`。 */
    Integer getSnapShotStatus();

    /** 厂商 `getSnapShotVersion:1476-1486`。 */
    String getSnapShotVersion();

    /** 厂商 `syncDatabaseInfo:1472-1474`：返回静态 `importStatus`。 */
    DatabaseSyncStatusEntity syncDatabaseInfo();

    /** 厂商 `createDatabaseBackupProcess:770-781`（`/v1/database/export/{databaseName}` 直接回写 stdout）。 */
    Process createDatabaseBackupProcess(String databaseName);

    /** 厂商 `genGaopBackup:827-829`（`/v1/database/export_framework`）。 */
    File genGaopBackup();

    // ---------------------------------------------------------------- 写

    /** 厂商 `addDatabaseInfo:566-590`（`@Transactional`）：建库 + 补 `g_database` + 导入 sql + 回写 sqlName。 */
    void addDatabaseInfo(String databaseName, String service, String desc, String userName,
                         InputStream fileStream, String originalFilename) throws IOException;

    /** 厂商 `updateDatabaseByName:592-602`。 */
    DatabaseEntity updateDatabaseByName(String databaseName, DatabaseDescribeVO describe);

    /** 厂商 `updateDatabaseInitSqlFile:619-653`。 */
    boolean updateDatabaseInitSqlFile(String databaseName, String userName,
                                     InputStream fileStream, String originalFilename) throws IOException;

    /** 厂商 `deleteDatabaseAllByName:713-731`：任一库不存在 → 400 `database.not.found.cannot.delete`（**不整体回滚**，前面的库已经删了）。 */
    void deleteDatabaseAllByName(List<String> databaseNames);

    /** 厂商 `deleteDatabaseByReset:733-761`：跳过 5 个系统库，逐个 try/catch 吞异常。 */
    void deleteDatabaseByReset();

    /** 厂商 `saveDataBaseSnapShot:966-1209`：把各业务库的初始化 sql（或全量 dump）压进一个 zip 并落盘。 */
    boolean saveDataBaseSnapShot(File zipFile, String path, Integer type) throws IOException;

    /** 厂商 `importSql:235-268`：`/v1/database/{databaseName}/import` 的主流程。 */
    DatabaseExecResultEntity importSql(InputStream fileStream, String fileName, String databaseName,
                                       long timeMillis, String userName, String saveFilePath) throws IOException;

    /**
     * 厂商 `inputStreamToFile:1310-1410`：解析上传 zip，把 `.sql`（含内嵌 `.zip` 里的 sql）转存到
     * `file`，并收集 `databaseUser.json` / `databaseUserRelation.json` / `databaseSql.json`。
     *
     * <p><b>注意</b>：厂商用 **Gson** 反序列化 JSON、且对 `databaseUserRelation.json` 额外做了
     * `StringEscapeUtils.unescapeJava`。本仓 domain 禁用 Jackson/Gson ⇒ 该方法由 **trigger 层**
     * 实现（`DataBaseController`），domain 拿到的是**已解析好的** `DatabaseImportInfoEntity`。
     */
    DatabaseImportSqlInfoEntity importDataBase(InputStream in, String fileName, Integer size,
                                              DatabaseImportInfoEntity importInfo) throws IOException;

    /** 厂商 `importSqlFromApplication:209-233`（软件/服务启动时自动导入，非 HTTP 面）。 */
    DatabaseExecResultEntity importSqlFromApplication(File sqlFile, String databaseName,
                                                      String service, String username) throws IOException;
}
