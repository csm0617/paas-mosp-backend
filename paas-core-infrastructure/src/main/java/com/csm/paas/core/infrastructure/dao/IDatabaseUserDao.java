package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.DatabaseUserPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 数据库用户管理（厂商 `DatabaseUserService`：`dao/DatabaseUserMapper` +
 * `dao/mysql/MysqlMapper` 里与数据库用户相关的那几条 SQL）。
 *
 * <p>两张来源：
 * <ul>
 *   <li>`g_database_user`（本地元数据表，建表脚本
 *       `db/migration/V0.0.8__add-database-user-table.sql`）；</li>
 *   <li>MySQL 管理连接的 `mysql.user` / `mysql.db`（厂商走第二数据源，本重写落在主数据源，
 *       与 `IDatabaseDao` 的 MySQL 段同一口径，**类 C**）。</li>
 * </ul>
 */
@Mapper
public interface IDatabaseUserDao {

    // ---------------------------------------------------------------- g_database_user

    /**
     * 厂商 `DatabaseUserMapper.select(where username in (...))` + `PageHelper.startPage`：
     * 分页参数是 PageHelper 的"页码/页大小"，这里由仓储层折成 `LIMIT/OFFSET`。
     */
    @Select("""
            <script>
            SELECT id, username, password, `desc`, updated_at, created_at FROM g_database_user
            WHERE username IN
            <foreach collection="usernames" item="u" open="(" separator="," close=")">#{u}</foreach>
            ORDER BY id LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<DatabaseUserPO> selectPageByUsernames(@Param("usernames") List<String> usernames,
                                               @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM g_database_user
            WHERE username IN
            <foreach collection="usernames" item="u" open="(" separator="," close=")">#{u}</foreach>
            </script>
            """)
    long countByUsernames(@Param("usernames") List<String> usernames);

    /** 厂商 `DatabaseUserMapper.queryDatabaseByName`（方法名写 databaseName，实际按 `username` 查）。 */
    @Select("SELECT id, username, password, `desc`, updated_at, created_at FROM g_database_user "
            + "WHERE username = #{username} LIMIT 1")
    DatabaseUserPO selectByUsername(@Param("username") String username);

    /** 厂商 `DatabaseUserMapper.insert`（`created_at`/`updated_at` 由调用方给）。 */
    @Insert("INSERT INTO g_database_user (username, password, `desc`, created_at, updated_at) "
            + "VALUES (#{username}, #{password}, #{desc}, #{createdAt}, #{updatedAt})")
    int insert(DatabaseUserPO user);

    /**
     * 厂商 `DatabaseUserMapper.updateDatabaseUser(password, desc, updatedAt, username)`：
     * 只更新这三列（`created_at` 不动）。
     */
    @Update("UPDATE g_database_user SET password = #{password}, `desc` = #{desc}, "
            + "updated_at = #{updatedAt} WHERE username = #{username}")
    void updateByUsername(@Param("username") String username, @Param("password") String password,
                          @Param("desc") String desc, @Param("updatedAt") Long updatedAt);

    /** 厂商 `DatabaseUserMapper.delete(where username = ?)` 与 `delete(d -> d)`（全表）。 */
    @Delete("DELETE FROM g_database_user WHERE username = #{username}")
    void deleteByUsername(@Param("username") String username);

    @Delete("DELETE FROM g_database_user")
    void deleteAll();

    /** 厂商 `DatabaseUserMapper.allDatabaseUsers()`：全部用户名（`DatabaseUserService` 的判存用）。 */
    @Select("SELECT username FROM g_database_user")
    List<String> allUsernames();

    // ---------------------------------------------------------------- mysql.user（类 C）

    /** 厂商 `MysqlMapper.mysqlUserListByUserName`。 */
    @Select("select User from mysql.user where Host = '%' and User != 'root' and User = #{userName}")
    List<String> mysqlUserListByUserName(@Param("userName") String userName);

    /** 厂商 `MysqlMapper.updateDatabaseUserName`：`ALTER USER #{databaseUser}@'%' IDENTIFIED BY #{password}`。 */
    @Update("ALTER USER #{databaseUser}@'%' IDENTIFIED BY #{password}")
    void updateMysqlUserName(@Param("password") String password,
                             @Param("databaseUser") String databaseUser);

    /** 厂商 `MysqlMapper.revokeAuthorization`：`revoke all on \`${databaseName}\`.* from \`${databaseUser}\`@'%'`。 */
    @Update("revoke all on `${databaseName}`.* from `${databaseUser}`@'%'")
    void revokeAuthorization(@Param("databaseName") String databaseName,
                             @Param("databaseUser") String databaseUser);
}
