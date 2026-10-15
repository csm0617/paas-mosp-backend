package com.csm.paas.core.domain.database.service;

import com.csm.paas.core.domain.database.model.entity.DatabaseUserEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserListEntity;

import java.util.List;
import java.util.Optional;

/**
 * 数据库用户管理（厂商 `service/DatabaseUserService.java` + `controller/DatabaseUserController.java`）。
 *
 * <p>厂商这一域走两张来源：`g_database_user`（本地元数据）与 `mysql.user`/`mysql.db`
 * （MySQL 管理连接的第二个数据源）。本重写**没有第二数据源**，MySQL 段落在主数据源上执行、
 * SQL 逐字照抄 ⇒ **类 C：本环境不可验证**。
 *
 * <p>厂商 `DatabaseUserService` 还带 `@Transactional`（`addDatabaseUser`/`deleteDatabaseAllUserByName`）；
 * 本重写按同样语义在方法上标注事务（`Revoke` 是单条 UPDATE，不需要）。
 */
public interface IDatabaseUserService {

    /**
     * 用户列表（厂商 `DatabaseUserService.list:56-71`）。
     *
     * <p>照抄点：先取 `mysql.user` 里 `Host='%' and User!='root'`（keyword 非空才 like）的用户名集合，
     * **集合为空直接返回空列表 + total 0**（不去查 `g_database_user`）；否则用该集合做
     * `g_database_user.username IN (...)` 过滤并按 PageHelper 分页。
     */
    DatabaseUserListEntity list(int page, int size, String keyword);

    /** 按用户名取单个用户（厂商 `DatabaseUserMapper.queryDatabaseByName`）；不存在返回 empty。 */
    Optional<DatabaseUserEntity> detailByUsername(String username);

    /**
     * 新增数据库用户（厂商 `DatabaseUserService.addDatabaseUser:124-149` + `DatabaseUserController.addDataBaseUser:56-64`）。
     *
     * <p>controller 两道校验照抄（消息是厂商原文的中文拼接，**不是 i18n 键**）：
     * `username.toLowerCase()=="root"` ⇒ 400；`existNameForAdd` ⇒ 400。
     * 服务层：同名已存在 ⇒ 删 `g_database_user` 旧行 + 删 mysql 用户（删不到只记日志）→ FLUSH；
     * 然后插 `g_database_user` + `create user` + FLUSH；插入后查不到该用户名 ⇒ 500 `global.add.failed`。
     */
    DatabaseUserEntity add(String username, String password, String desc);

    /**
     * 修改数据库用户（厂商 `DatabaseUserService.modifyDatabaseUser:211-221`）。
     *
     * <p>顺序照抄：先查 `g_database_user`（查不到 ⇒ 400 `user.not.found.modify.failed`）→
     * `ALTER USER` 改密码 → FLUSH → 更新 `password`/`desc`/`updated_at` → 返回**重新查出来的**行。
     */
    DatabaseUserEntity modify(String username, String password, String desc);

    /**
     * 批量删除（厂商 `DatabaseUserService.deleteDatabaseAllUserByName:182-194`）：
     * 逐个判存在，不存在 ⇒ 400 `user.not.found.cannot.delete`（**立即中断**，前面的已删）；
     * 存在 ⇒ 删 `g_database_user` 行 + `drop user`。**注意厂商这里不 FLUSH**，照抄。
     */
    void deleteAllByNames(List<String> usernames);

    /**
     * 取消某用户对某库的授权（厂商 `DatabaseUserService.revokeAuthorization:223-240`）。
     *
     * <p>四道闸顺序照抄（每道都是 400）：
     * ① 用户在 `g_database_user` 不存在 ⇒ `user.not.found.modify.failed`；
     * ② 库不存在（`show databases`）⇒ `database.not.found`（带库名）；
     * ③ `mysql.db` 里该库没有任何授权用户 ⇒ `database.not.set.user.access.authority`（带库名）；
     * ④ 该用户不在授权名单里 ⇒ `database.user.not.access.authority`（带用户名）。
     * 通过 ⇒ 执行 MySQL 的 `revoke all on &lt;库&gt;.* from &lt;用户&gt;@'%'`。
     */
    void revokeAuthorization(String databaseName, String databaseUser);
}
