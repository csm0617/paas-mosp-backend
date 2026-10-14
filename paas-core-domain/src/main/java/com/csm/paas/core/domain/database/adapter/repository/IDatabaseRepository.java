package com.csm.paas.core.domain.database.adapter.repository;

import com.csm.paas.core.domain.database.model.entity.BusinessDatabaseNameEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseEntity;
import com.csm.paas.core.domain.database.model.entity.DatabasePermissionEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeItemEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据库域仓储（在原 `g_database_record` 之上补齐 `/v1/database` 需要的两类访问）。
 *
 * <h3>一、本地 gaop 库（`g_database_record` + `g_database`）</h3>
 * 走主数据源（`spring.datasource`，库 `gaop`）：
 * <ul>
 *   <li>`g_database_record`：厂商 `DatabaseRecordService.getAllDatabaseRecord:25-33`（原有方法，未改动）</li>
 *   <li>`g_database`：厂商 `DataBaseManagerMapper`（`queryDatabaseDesc` / `createDatabaseInfo` /
 *       `queryDatabaseByName` / `updateDatabaseByName` / `updateDatabaseByLastUser` / `deleteDataBaseDesc`）
 *       + `DatabaseMapper`（dynamic-sql：`insertSelective` / `updateByPrimaryKey` /
 *       `updateByPrimaryKeySelective` / `delete where database_name = ?`）</li>
 * </ul>
 *
 * <h3>二、MySQL 管理连接（`mysql` 库 / `information_schema` / `mysql.*`）</h3>
 * 厂商用**第二个 Druid 数据源** `@Qualifier("mysql")`（配置前缀 `spring.datasource.third`，
 * 见 `config/multidb/MultiDataSource.java:33-38`），通过 `@DataSource(DbType.MYSQL)` AOP 切换；
 * 所有 SQL 都在 `dao/mysql/MysqlMapper.java`。本重写**没有**引入第二数据源
 * （硬性规则：不改 application.yml、不加依赖、不动白名单外文件），这些语句一律落在主数据源上执行
 * ——**同一台 MySQL、不同的库/系统表**，只要账号有权限语义等价；若部署时两者真的是**不同主机**
 * （厂商 k8s profile 就是 `gaop-mysql` vs `gaop-radondb-mysql-leader`），则这些端点需要在
 * infrastructure 里补一个 `@Value("${gaop.database.admin.url:...}")` 的独立 DataSource + Mapper。
 * **这一点已写进回报的"未做项"。**
 *
 * <p><b>类 C：本环境不可验证</b>——本环境只有一台 MySQL（`gaop` 库），没有厂商的第二数据源主机，
 * 所有 `mysql.*` / `information_schema` 语句无法与厂商对拉。
 */
public interface IDatabaseRepository {

    // ------------------------------------------------------------------ g_database_record（原有）

    /** 全部记录，按 `updated_at DESC`（厂商 `DatabaseRecordService.getAllDatabaseRecord:25-33`）。 */
    List<DatabaseRecordEntity> queryRecordPage(int offset, int size);

    long countRecords();

    /** 写入一条导入记录（厂商 `DatabaseService.addDatabaseRecord:1266-1277`、`importSqlFromApplication:223-231`）。 */
    void insertRecord(DatabaseRecordEntity record);

    // ------------------------------------------------------------------ g_database（新增）

    /**
     * 厂商 `DataBaseManagerMapper.queryDatabaseDesc`：`select * from g_database where database_name=#{name}`。
     * <p>厂商返回 `List` 并只判 `isEmpty()`，所以这里保持 List 语义。
     */
    List<DatabaseEntity> queryDatabaseRows(String databaseName);

    /** 厂商 `DataBaseManagerMapper.queryDatabaseByName`：按库名取一行（取不到 empty，厂商返回 null）。 */
    Optional<DatabaseEntity> queryDatabaseByName(String databaseName);

    /** 厂商 `DatabaseMapper.select(allRows())`：`g_database` 全表。 */
    List<DatabaseEntity> queryAllDatabaseRows();

    /** 厂商 `DataBaseManagerMapper.createDatabaseInfo`：只写 `database_name/service/desc`。 */
    void insertDatabaseInfo(String databaseName, String service, String desc);

    /** 厂商 `DatabaseMapper.insertSelective`：写非 null 列。 */
    void insertDatabaseSelective(DatabaseEntity database);

    /** 厂商 `DatabaseMapper.updateByPrimaryKey`：整行 update。 */
    void updateDatabaseByPrimaryKey(DatabaseEntity database);

    /** 厂商 `DatabaseMapper.updateByPrimaryKeySelective`：只写非 null 列。 */
    void updateDatabaseByPrimaryKeySelective(DatabaseEntity database);

    /** 厂商 `DataBaseManagerMapper.updateDatabaseByName`：`set desc=#{desc}, service=#{service}`。 */
    void updateDatabaseDescription(String databaseName, String service, String desc);

    /** 厂商 `DataBaseManagerMapper.updateDatabaseByLastUser`：`set username=#{userName}`。 */
    void updateDatabaseLastUser(String databaseName, String userName);

    /** 厂商 `DataBaseManagerMapper.deleteDataBaseDesc`：删 `g_database` 行。 */
    void deleteDatabaseRow(String databaseName);

    /** 厂商 `DatabaseMapper.delete(where database_name = ?)`（`DatabaseService.deleteDatabase:562-564`）。 */
    void deleteDatabaseRowsByName(String databaseName);

    // ------------------------------------------------------------------ MySQL 管理连接（新增，类 C）

    /** 厂商 `MysqlMapper.showDataBases`：`show databases`。 */
    List<String> showDatabases();

    /** 厂商 `MysqlMapper.allDatabasesTableCount`：业务库 + 表数（排除 `gaop`/系统库）。 */
    List<BusinessDatabaseNameEntity> allDatabasesTableCount(String keywordOrNull);

    /** 厂商 `MysqlMapper.createDatabase`：`create database ${dataBaseName} default character SET utf8mb4 COLLATE utf8mb4_general_ci`。 */
    void createDatabase(String quotedDatabaseName);

    /** 厂商 `MysqlMapper.deleteDataBaseAllByName`：`drop database if exists \`${name}\``。 */
    void dropDatabase(String databaseName);

    /** 厂商 `MysqlMapper.queryAccessUser`：`select user as accessuser from mysql.user where user=#{accessUser}`（单值，可能 null）。 */
    String queryAccessUser(String accessUser);

    /**
     * 厂商 `MysqlMapper.databasesAccessControl`：`GRANT ALL ON \`{db}\`.* to \`{user}\`@'%'`
     * ——厂商用 **`@Select`** 执行一条 GRANT（见 `DatabasePermissionEntity` 的 javadoc，属照抄的怪点）。
     */
    List<DatabasePermissionEntity> databasesAccessControl(String databaseName, String accessUser);

    /** 厂商 `MysqlMapper.databaseBelongUsers`：`select user from mysql.db where Db=#{db} and Host='%'`。 */
    List<String> databaseBelongUsers(String databaseName);

    /** 厂商 `MysqlMapper.mysqlUserList`：`mysql.user` 里 `Host='%' and User!='root'`（keyword 非空才 like）。 */
    List<String> mysqlUserList(String keywordOrNull);

    /** 厂商 `MysqlMapper.insertUser`：`create user \`{user}\`@'%' identified by '{password}'`。 */
    int insertMysqlUser(String databaseUser, String password);

    /** 厂商 `MysqlMapper.deleteDatabaseAllUserByName`：`drop user \`{user}\`@'%'`。 */
    void dropMysqlUser(String databaseUser);

    /** 厂商 `MysqlMapper.refreshPermission`：`FLUSH PRIVILEGES`。 */
    void refreshPermission();

    /** 厂商 `MysqlMapper.allVariables`（`@MapKey("variableName")`）：`show variables` → 变量名到值。 */
    Map<String, String> allVariables();

    /** 厂商 `MysqlMapper.getStorageSize`：`sum(truncate(data_length/1024/1024,2)) from information_schema.tables where table_schema='mysql'`。 */
    int getStorageSizeMb();

    /** 厂商 `MysqlMapper.getMysqlCreatedAt`：`mysql.user` 表的 `CREATE_TIME`。 */
    String getMysqlCreatedAt();

    /** 厂商 `MysqlMapper.getDbTableSize`：所有 BASE TABLE 的库/表/大小（MB）。 */
    List<DatabaseTableSizeItemEntity> getDbTableSize();

    // ------------------------------------------------------------------ g_file（新增，快照状态用）

    /**
     * 厂商 `FileMapper.select(where file_project_id=? and name=?)`（`DatabaseService.getSnapShotStatus:903-918`、
     * `getSnapShotVersion:1478-1485`、`getExistDatabaseFileInfo:2079-2082`）等价的存在性判断。
     */
    long countFiles(String fileProjectId, String name);

    /** 厂商 `file.getUpdatedAt()`：取该 (project, name) 行的 `updated_at`（取不到 null）。 */
    Long queryFileUpdatedAt(String fileProjectId, String name);

    /** 厂商 `FileMapper.insertSelective`（`saveDataBaseSnapShot:1188-1198`）。 */
    void insertFile(String id, String name, String fileProjectId, String path,
                    long createdAt, long updatedAt, boolean deleted, long size);

    /** 厂商 `FileMapper.updateByPrimaryKey`（`saveDataBaseSnapShot:1083-1086`）。 */
    void updateFile(String fileProjectId, String name, String path, long size, long updatedAt);
}
