package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.DatabaseRecordPO;
import lombok.Data;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 数据库域本地表 + MySQL 管理连接。
 *
 * <h3>一、本地 gaop 库</h3>
 * <ul>
 *   <li>`g_database_record`：厂商 `DatabaseRecordService.getAllDatabaseRecord:25-33`（原有方法，未改动）</li>
 *   <li>`g_database`：厂商 `DataBaseManagerMapper` / `DatabaseMapper`（见各方法 javadoc）</li>
 *   <li>`g_file`：厂商 `FileMapper`（快照状态与版本号），只用到 `id,name,file_project_id,path,created_at,updated_at,deleted,size`</li>
 * </ul>
 *
 * <h3>二、MySQL 管理连接（类 C）</h3>
 * 厂商这部分走**独立的第二个数据源** `@Qualifier("mysql")`（`spring.datasource.third`，
 * 见 `config/multidb/MultiDataSource.java:33-38` + `dao/mysql/MysqlMapper.java` 的 `@DataSource(DbType.MYSQL)`）。
 * 本重写**没有**第二数据源，这些语句落在主数据源上执行；SQL 逐字照抄。
 * <p>行类型用**嵌套类**（`DatabaseRow` 等）承载，避免新增 `dao/po/*` 文件（本任务的文件白名单只允许
 * `dao/po/Backup*.java`）。
 */
@Mapper
public interface IDatabaseDao {

    // ================================================================ g_database_record

    /** 厂商 `DatabaseRecordService.getAllDatabaseRecord`：`updated_at DESC` 分页。 */
    @Select("""
            SELECT id, real_store_filename, exec_file_name, database_name, updated_at,
                   exec_fault_message, status
            FROM g_database_record ORDER BY updated_at DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<DatabaseRecordPO> selectRecordPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM g_database_record")
    long selectRecordCount();

    /** 厂商 `DatabaseService.addDatabaseRecord:1266-1277`（insert 全列）。 */
    @Insert("""
            INSERT INTO g_database_record
              (id, real_store_filename, exec_file_name, database_name, updated_at, status, exec_fault_message)
            VALUES (#{id}, #{realStoreFilename}, #{execFileName}, #{databaseName}, #{updatedAt},
                    #{status}, #{execFaultMessage})
            """)
    void insertRecord(DatabaseRecordPO record);

    // ================================================================ g_database

    @Select("SELECT id, `database_name`, `username`, `desc`, `sql_name`, `service` "
            + "FROM g_database WHERE `database_name` = #{databaseName}")
    List<DatabaseRow> selectDatabaseRows(@Param("databaseName") String databaseName);

    @Select("SELECT id, `database_name`, `username`, `desc`, `sql_name`, `service` "
            + "FROM g_database WHERE `database_name` = #{databaseName} LIMIT 1")
    Optional<DatabaseRow> selectDatabaseByName(@Param("databaseName") String databaseName);

    @Select("SELECT id, `database_name`, `username`, `desc`, `sql_name`, `service` FROM g_database")
    List<DatabaseRow> selectAllDatabaseRows();

    /** 厂商 `DataBaseManagerMapper.createDatabaseInfo`：只写三列。 */
    @Insert("INSERT INTO g_database (database_name, `service`, `desc`) "
            + "VALUES (#{databaseName}, #{service}, #{desc})")
    void insertDatabaseInfo(@Param("databaseName") String databaseName, @Param("service") String service,
                            @Param("desc") String desc);

    /** 厂商 `DatabaseMapper.insertSelective`：写非 null 列。 */
    @Insert("<script>INSERT INTO g_database "
            + "<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">"
            + "<if test=\"databaseName != null\">`database_name`,</if>"
            + "<if test=\"username != null\">`username`,</if>"
            + "<if test=\"desc != null\">`desc`,</if>"
            + "<if test=\"sqlName != null\">`sql_name`,</if>"
            + "<if test=\"service != null\">`service`,</if>"
            + "</trim><trim prefix=\"VALUES (\" suffix=\")\" suffixOverrides=\",\">"
            + "<if test=\"databaseName != null\">#{databaseName},</if>"
            + "<if test=\"username != null\">#{username},</if>"
            + "<if test=\"desc != null\">#{desc},</if>"
            + "<if test=\"sqlName != null\">#{sqlName},</if>"
            + "<if test=\"service != null\">#{service},</if>"
            + "</trim></script>")
    void insertDatabaseSelective(DatabaseRow database);

    /** 厂商 `DatabaseMapper.updateByPrimaryKey`（整行，null 也会写回）。 */
    @Update("UPDATE g_database SET `database_name` = #{databaseName}, `username` = #{username}, "
            + "`desc` = #{desc}, `sql_name` = #{sqlName}, `service` = #{service} WHERE id = #{id}")
    void updateDatabaseByPrimaryKey(DatabaseRow database);

    /** 厂商 `DatabaseMapper.updateByPrimaryKeySelective`。 */
    @Update("<script>UPDATE g_database <set>"
            + "<if test=\"databaseName != null\">`database_name` = #{databaseName},</if>"
            + "<if test=\"username != null\">`username` = #{username},</if>"
            + "<if test=\"desc != null\">`desc` = #{desc},</if>"
            + "<if test=\"sqlName != null\">`sql_name` = #{sqlName},</if>"
            + "<if test=\"service != null\">`service` = #{service},</if>"
            + "</set> WHERE id = #{id}</script>")
    void updateDatabaseByPrimaryKeySelective(DatabaseRow database);

    /** 厂商 `DataBaseManagerMapper.updateDatabaseByName`：`set desc=#{desc}, service=#{service}`。 */
    @Update("UPDATE g_database SET `desc` = #{desc}, `service` = #{service} WHERE `database_name` = #{databaseName}")
    void updateDatabaseDescription(@Param("databaseName") String databaseName,
                                   @Param("service") String service, @Param("desc") String desc);

    /** 厂商 `DataBaseManagerMapper.updateDatabaseByLastUser`：`set username=#{userName}`。 */
    @Update("UPDATE g_database SET `username` = #{userName} WHERE `database_name` = #{databaseName}")
    void updateDatabaseLastUser(@Param("databaseName") String databaseName, @Param("userName") String userName);

    /** 厂商 `DataBaseManagerMapper.deleteDataBaseDesc`。 */
    @Delete("DELETE FROM g_database WHERE `database_name` = #{databaseName}")
    void deleteDatabaseRow(@Param("databaseName") String databaseName);

    /** 厂商 `DatabaseMapper.delete(where database_name = ?)`。 */
    @Delete("DELETE FROM g_database WHERE `database_name` = #{databaseName}")
    void deleteDatabaseRowsByName(@Param("databaseName") String databaseName);

    // ================================================================ MySQL 管理连接（类 C）

    @Select("show databases")
    List<String> showDataBases();

    /**
     * 厂商 `MysqlMapper.allDatabasesTableCount`（`<when test='keyword!=null'>` 才加 like）。
     * <p>照抄点：排除 `gaop`/系统库；`count(t.table_name)` 是**左连接后的行数**（无表的库为 1，不是 0）。
     */
    @Select("<script>select count(t.table_name) as tableCount, s.schema_name as name from "
            + "information_schema.SCHEMATA as s "
            + "left join information_schema.`TABLES` t on t.table_schema = s.schema_name "
            + "where s.schema_name not in ('gaop','sys','information_schema','performance_schema','mysql')"
            + "<if test=\"keyword != null\"> and s.schema_name like concat('%',#{keyword},'%')</if> "
            + "GROUP BY name ORDER BY name</script>")
    List<BusinessDatabaseRow> allDatabasesTableCount(@Param("keyword") String keyword);

    /** 厂商 `MysqlMapper.createDatabase`：`${}` 原样拼串（**调用方负责加反引号**）。 */
    @Update("create database ${quotedDatabaseName} default character SET utf8mb4 COLLATE utf8mb4_general_ci")
    void createDatabase(@Param("quotedDatabaseName") String quotedDatabaseName);

    /** 厂商 `MysqlMapper.deleteDataBaseAllByName`：`drop database if exists \`${name}\``。 */
    @Update("drop database if exists `${databaseName}`")
    void dropDatabase(@Param("databaseName") String databaseName);

    /** 厂商 `MysqlMapper.queryAccessUser`：取单值。 */
    @Select("select user as accessuser from mysql.`user` where user = #{accessUser}")
    String queryAccessUser(@Param("accessUser") String accessUser);

    /**
     * 厂商 `MysqlMapper.databasesAccessControl`：**用 `@Select` 执行 GRANT**（怪点，见
     * `DatabasePermissionEntity` javadoc）。显式 `@Results` 把列 `accessuser` 映到 `accessUser`
     * ——照抄厂商的列别名（它的 `mapUnderscoreToCamelCase` 对 `accessuser` 不生效，
     * 所以厂商那边的 `accessUser` 实际恒为 null；这里如实保留该行为是不可能的，
     * 因为 GRANT 正常返回空结果集 ⇒ 两种写法都拿不到行）。
     */
    @Select("GRANT ALL ON  `${databaseName}`.* to `${accessUser}`@'%'")
    @Results(id = "DatabasePermissionRowMap", value = {
            @Result(column = "databaseName", property = "databaseName"),
            @Result(column = "accessuser", property = "accessUser")})
    List<DatabasePermissionRow> databasesAccessControl(@Param("databaseName") String databaseName,
                                                       @Param("accessUser") String accessUser);

    @Select("select `user` from mysql.db where Db = #{databaseName} and Host = '%'")
    List<String> databaseBelongUsers(@Param("databaseName") String databaseName);

    @Select("<script>select User from mysql.user where Host = '%' and User != 'root' "
            + "<if test=\"keyword != null\">and User like concat('%',#{keyword},'%')</if></script>")
    List<String> mysqlUserList(@Param("keyword") String keyword);

    /** 厂商 `MysqlMapper.insertUser`：`${}` 原样拼（密码明文进 SQL，照抄）。 */
    @Update("create user `${databaseUser}`@'%' identified by '${password}'")
    int insertMysqlUser(@Param("databaseUser") String databaseUser, @Param("password") String password);

    @Update("drop user `${databaseUser}`@'%'")
    void deleteDatabaseAllUserByName(@Param("databaseUser") String databaseUser);

    @Update("FLUSH PRIVILEGES")
    void refreshPermission();

    /**
     * 厂商 `MysqlMapper.allVariables`（`@MapKey("variableName")`）：`show variables`。
     * <p>厂商返回 `Map&lt;String,Variable&gt;`（MyBatis `@MapKey`）；本仓改成 `List&lt;VariableRow&gt;`，
     * 由仓储层折成 `Map&lt;变量名,值&gt;`（`map-underscore-to-camel-case` 对 `Variable_name` 不生效，
     * 所以这里显式 `@Results`）。
     */
    @Select("show variables")
    @Results(id = "MysqlVariableRowMap", value = {
            @Result(column = "Variable_name", property = "variableName"),
            @Result(column = "Value", property = "value")})
    List<VariableRow> allVariables();

    @Select("select sum(truncate(data_length/1024/1024, 2)) as data_size "
            + "from information_schema.tables where table_schema='mysql'")
    Integer getStorageSize();

    @Select("SELECT CREATE_TIME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mysql' and table_name='user'")
    String getMysqlCreatedAt();

    @Select("SELECT table_schema AS 'databaseName', table_name AS 'tableName', "
            + "ROUND((data_length + index_length) / 1024 / 1024, 2) AS 'size' "
            + "FROM information_schema.tables WHERE table_type = 'BASE TABLE' "
            + "AND table_schema NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') "
            + "ORDER BY databaseName asc, size desc")
    List<TableSizeRow> getDbTableSize();

    // ================================================================ g_file（快照状态）

    @Select("SELECT COUNT(*) FROM g_file WHERE file_project_id = #{fileProjectId} AND name = #{name}")
    long countFiles(@Param("fileProjectId") String fileProjectId, @Param("name") String name);

    @Select("SELECT updated_at FROM g_file WHERE file_project_id = #{fileProjectId} AND name = #{name} LIMIT 1")
    Long selectFileUpdatedAt(@Param("fileProjectId") String fileProjectId, @Param("name") String name);

    /** 厂商 `FileMapper.insertSelective`（`saveDataBaseSnapShot:1188-1198`）。 */
    @Insert("INSERT INTO g_file (id, name, file_project_id, path, created_at, updated_at, deleted, size) "
            + "VALUES (#{id}, #{name}, #{fileProjectId}, #{path}, #{createdAt}, #{updatedAt}, #{deleted}, #{size})")
    void insertFile(@Param("id") String id, @Param("name") String name,
                    @Param("fileProjectId") String fileProjectId, @Param("path") String path,
                    @Param("createdAt") long createdAt, @Param("updatedAt") long updatedAt,
                    @Param("deleted") boolean deleted, @Param("size") long size);

    /** 厂商 `FileMapper.updateByPrimaryKey`（只更新 path/size/updated_at 这三个业务列）。 */
    @Update("UPDATE g_file SET path = #{path}, size = #{size}, updated_at = #{updatedAt} "
            + "WHERE file_project_id = #{fileProjectId} AND name = #{name}")
    void updateFile(@Param("fileProjectId") String fileProjectId, @Param("name") String name,
                    @Param("path") String path, @Param("size") long size, @Param("updatedAt") long updatedAt);

    // ================================================================ 行类型（嵌套，避免新增 dao/po 文件）

    /** `g_database` 一行（列名带反引号，见 `dao/DatabaseDynamicSqlSupport.java:21-26`）。 */
    @Data
    class DatabaseRow {
        private Long id;
        private String databaseName;
        private String username;
        private String desc;
        private String sqlName;
        private String service;
    }

    /** `allDatabasesTableCount` 一行（`name` + `tableCount`）。 */
    @Data
    class BusinessDatabaseRow {
        private String name;
        private Long tableCount;
    }

    /** `databasesAccessControl` 一行（厂商 `DatabasePermission`）。 */
    @Data
    class DatabasePermissionRow {
        private String databaseName;
        private String accessUser;
    }

    /** `getDbTableSize` 一行（厂商 `DatabaseTableSizeItem`）。 */
    @Data
    class TableSizeRow {
        private String databaseName;
        private String tableName;
        private BigDecimal size;
    }

    /** `show variables` 一行（厂商 `dao/mysql/Variable`）。 */
    @Data
    class VariableRow {
        private String variableName;
        private String value;
    }
}
