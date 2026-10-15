package com.csm.paas.core.domain.database.adapter.repository;

import com.csm.paas.core.domain.database.model.entity.DatabaseUserEntity;

import java.util.List;
import java.util.Optional;

/**
 * 数据库用户仓储（厂商 `dao/DatabaseUserMapper` + `dao/mysql/MysqlMapper` 的用户相关 SQL）。
 *
 * <p>与 `IDatabaseRepository` 的分工：这里只管"数据库用户"这一子域（`g_database_user` + `mysql.user`
 * 的用户名/密码/授权），数据库本身的增删改查仍在 `IDatabaseRepository`。
 */
public interface IDatabaseUserRepository {

    /** `g_database_user` 里 username 命中给定集合的行，按 `id` 分页（厂商 `DatabaseUserService.list:64-66`）。 */
    List<DatabaseUserEntity> queryPageByUsernames(List<String> usernames, int offset, int size);

    /** 同一套 WHERE 的总数（PageHelper 的 count）。 */
    long countByUsernames(List<String> usernames);

    /**
     * 按用户名取（厂商 `DatabaseUserMapper.queryDatabaseByName`——方法名写的是 databaseName，
     * 实际 SQL 是 `where username = ?`）。
     */
    Optional<DatabaseUserEntity> queryByUsername(String username);

    void insert(DatabaseUserEntity user);

    /** 只更新 `password`/`desc`/`updated_at` 三列（厂商 `DatabaseUserMapper.updateDatabaseUser`）。 */
    void updatePasswordAndDesc(String username, String password, String desc, long updatedAt);

    void deleteByUsername(String username);

    /** 厂商 `databaseUserMapper.delete(d -> d)`：清空 `g_database_user`。 */
    void deleteAll();

    /** 厂商 `DatabaseUserMapper.allDatabaseUsers()`：全部用户名。 */
    List<String> allUsernames();

    /** 厂商 `MysqlMapper.mysqlUserListByUserName`。 */
    List<String> mysqlUserListByUserName(String userName);

    /** 厂商 `MysqlMapper.updateDatabaseUserName`。 */
    void updateMysqlUserName(String password, String databaseUser);

    /** 厂商 `MysqlMapper.revokeAuthorization`。 */
    void revokeAuthorization(String databaseName, String databaseUser);
}
