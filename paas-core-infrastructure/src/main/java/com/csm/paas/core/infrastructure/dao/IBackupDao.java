package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.BackupPO;
import com.csm.paas.core.infrastructure.dao.po.BackupSettingPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 备份域本地表：`g_backup` + `g_backup_setting`。
 *
 * <p>厂商出处：
 * <ul>
 *   <li>`BackupMapper`（MyBatis dynamic-sql，`dao/BackupMapper.java`）——等价 SQL 见
 *       `BackService.getDatabaseBackupList:188`（`isLikeWhenPresent(database_name)` + `created_at desc`）、
 *       `getBackupInfo:643`（`updateByPrimaryKey`）、`updateBackUpByError:631`（`updateByPrimaryKeySelective`）</li>
 *   <li>`BackupSettingMapper`（`getBackupSettingList:1565/1570/1576/1583/1590/1595`、
 *       `changeBackupSetting:1603/1609`）</li>
 * </ul>
 * <p>分页照抄 PageHelper 的等价物：MySQL `LIMIT #{size} OFFSET #{offset}`；
 * `total` 用**同一 where 条件**的 `COUNT(*)`。
 *
 * <p><b>实现细节</b>：SQL 一律用普通字符串常量拼接（不用 Java 文本块）——
 * 文本块会**剥掉行尾空白**，`"...SELECT " + COLUMNS + ...` 会被粘成 `SELECTid, ...`。
 */
@Mapper
public interface IBackupDao {

    String BACKUP_COLUMNS =
            "id, database_key, database_name, database_filename, size, created_at, updated_at, msg";

    String SELECT_BACKUP_PAGE_SQL = "<script>SELECT " + BACKUP_COLUMNS + " FROM g_backup "
            + "<if test=\"likeKeyword != null\"> WHERE database_name LIKE #{likeKeyword} </if> "
            + "ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}</script>";

    String COUNT_BACKUPS_SQL = "<script>SELECT COUNT(*) FROM g_backup "
            + "<if test=\"likeKeyword != null\"> WHERE database_name LIKE #{likeKeyword} </if></script>";

    String UPDATE_BACKUP_SELECTIVE_SQL = "<script>UPDATE g_backup "
            + "<set>"
            + "<if test=\"databaseKey != null\">database_key = #{databaseKey},</if>"
            + "<if test=\"databaseName != null\">database_name = #{databaseName},</if>"
            + "<if test=\"databaseFilename != null\">database_filename = #{databaseFilename},</if>"
            + "<if test=\"size != null\">size = #{size},</if>"
            + "<if test=\"createdAt != null\">created_at = #{createdAt},</if>"
            + "<if test=\"updatedAt != null\">updated_at = #{updatedAt},</if>"
            + "<if test=\"msg != null\">msg = #{msg},</if>"
            + "</set> WHERE id = #{id}</script>";

    /** `database_name` 模糊（null=不过滤）+ `created_at DESC` 分页。 */
    @Select(SELECT_BACKUP_PAGE_SQL)
    List<BackupPO> selectBackupPage(@Param("likeKeyword") String likeKeyword,
                                    @Param("offset") int offset, @Param("size") int size);

    @Select(COUNT_BACKUPS_SQL)
    long countBackups(@Param("likeKeyword") String likeKeyword);

    /** 厂商到处用 `select(...).get(0)`：这里取 `id` 最小的一行，保持确定性。 */
    @Select("SELECT " + BACKUP_COLUMNS
            + " FROM g_backup WHERE database_key = #{databaseKey} ORDER BY id LIMIT 1")
    Optional<BackupPO> selectByKey(@Param("databaseKey") String databaseKey);

    /** 厂商 `BackupMapper.updateByPrimaryKey`（整行，null 也会写回）。 */
    @Update("UPDATE g_backup SET database_key = #{databaseKey}, database_name = #{databaseName}, "
            + "database_filename = #{databaseFilename}, size = #{size}, created_at = #{createdAt}, "
            + "updated_at = #{updatedAt}, msg = #{msg} WHERE id = #{id}")
    void updateByPrimaryKey(BackupPO backup);

    /** 厂商 `BackupMapper.updateByPrimaryKeySelective`（dynamic-sql 的 `<set>` 语义）。 */
    @Update(UPDATE_BACKUP_SELECTIVE_SQL)
    void updateByPrimaryKeySelective(BackupPO backup);

    // ---------------------------------------------------------------- g_backup_setting

    @Select("SELECT id, database_name, type FROM g_backup_setting")
    List<BackupSettingPO> selectAllSettings();

    @Select("SELECT id, database_name, type FROM g_backup_setting LIMIT #{size} OFFSET #{offset}")
    List<BackupSettingPO> selectSettingPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM g_backup_setting")
    long countSettings();

    @Select("SELECT id, database_name, type FROM g_backup_setting WHERE database_name = #{databaseName} LIMIT 1")
    Optional<BackupSettingPO> selectSettingByName(@Param("databaseName") String databaseName);

    /** 厂商 `insertSelective`：只写 `database_name` 与 `type`。 */
    @Insert("INSERT INTO g_backup_setting (database_name, type) VALUES (#{databaseName}, #{type})")
    void insertSetting(@Param("databaseName") String databaseName, @Param("type") Integer type);

    @Delete("DELETE FROM g_backup_setting WHERE database_name = #{databaseName}")
    void deleteSetting(@Param("databaseName") String databaseName);

    @Update("UPDATE g_backup_setting SET type = #{type} WHERE database_name = #{databaseName}")
    int updateSettingType(@Param("databaseName") String databaseName, @Param("type") Integer type);

    @Delete("DELETE FROM g_backup_setting")
    void deleteAllSettings();
}
