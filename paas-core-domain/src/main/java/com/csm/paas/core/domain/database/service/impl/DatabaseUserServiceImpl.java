package com.csm.paas.core.domain.database.service.impl;

import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.adapter.repository.IDatabaseUserRepository;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserListEntity;
import com.csm.paas.core.domain.database.service.IDatabaseUserService;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 数据库用户管理实现（规格：厂商 `service/DatabaseUserService.java`，
 * 出处逐条写在 {@link IDatabaseUserService} 的方法 javadoc）。
 *
 * <p><b>类 C：本环境不可验证</b>——MySQL 管理连接（`create user`/`ALTER USER`/`revoke`）
 * 与 `mysql.user`/`mysql.db` 查询在本环境到不了对端；`g_database_user` 的读写是纯库路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseUserServiceImpl implements IDatabaseUserService {

    private final IDatabaseUserRepository databaseUserRepository;
    private final IDatabaseRepository databaseRepository;

    @Override
    public DatabaseUserListEntity list(int page, int size, String keyword) {
        List<String> mysqlUserList = databaseRepository.mysqlUserList(keyword);
        if (mysqlUserList.isEmpty()) {
            return DatabaseUserListEntity.builder().databaseUsers(List.of()).total(0L).build();
        }
        long total = databaseUserRepository.countByUsernames(mysqlUserList);
        List<DatabaseUserEntity> users = databaseUserRepository.queryPageByUsernames(
                mysqlUserList, (page - 1) * size, size);
        return DatabaseUserListEntity.builder().databaseUsers(users).total(total).build();
    }

    @Override
    public Optional<DatabaseUserEntity> detailByUsername(String username) {
        return databaseUserRepository.queryByUsername(username);
    }

    @Override
    @Transactional
    public DatabaseUserEntity add(String username, String password, String desc) {
        if (username != null && "root".equals(username.toLowerCase())) {
            throw new WrongArgumentException(String.format("不能添加数据库用户 %s ", username));
        }
        if (existNameForAdd(username)) {
            throw new WrongArgumentException(String.format("数据库用户 %s 已存在", username));
        }
        if (existName(username)) {
            // 厂商：同名已存在 ⇒ 删掉旧的 g_database_user 行与 mysql 用户（删不到只记日志）→ FLUSH
            databaseUserRepository.deleteByUsername(username);
            try {
                databaseRepository.dropMysqlUser(username);
            } catch (Exception e) {
                log.info("业务数据库中不存在该用户不进行删除操作");
            }
            databaseRepository.refreshPermission();
            log.info("删除旧的存放在gaop-mysql中的用户数据与mysql中的用户{}", username);
        }
        long now = System.currentTimeMillis();
        DatabaseUserEntity user = DatabaseUserEntity.builder()
                .username(username).password(password).desc(desc)
                .createdAt(now).updatedAt(now).build();
        databaseUserRepository.insert(user);
        databaseRepository.insertMysqlUser(username, password);
        databaseRepository.refreshPermission();
        if (databaseUserRepository.countByUsernames(List.of(username)) == 0L) {
            throw new SystemException("global.add.failed");
        }
        return user;
    }

    /** 厂商 `DatabaseUserService.existName:151-154`：`g_database_user` 里 username 命中数 > 0。 */
    private boolean existName(String username) {
        return databaseUserRepository.countByUsernames(List.of(username)) > 0L;
    }

    /**
     * 厂商 `DatabaseUserService.existNameForAdd:156-169`：`g_database_user` 里没有该 user ⇒ false；
     * 有但 `mysql.user` 里没有 ⇒ **把元数据里的用户补建到 mysql** 再返回 true。
     */
    private boolean existNameForAdd(String username) {
        List<DatabaseUserEntity> databaseUsers = databaseUserRepository.queryByUsername(username)
                .map(List::of).orElse(List.of());
        if (databaseUsers.isEmpty()) {
            return false;
        }
        List<String> userNames = databaseUserRepository.mysqlUserListByUserName(username);
        if (userNames.isEmpty()) {
            for (DatabaseUserEntity user : databaseUsers) {
                databaseRepository.insertMysqlUser(user.getUsername(), user.getPassword());
                databaseRepository.refreshPermission();
            }
        }
        return true;
    }

    @Override
    @Transactional
    public DatabaseUserEntity modify(String username, String password, String desc) {
        if (databaseUserRepository.queryByUsername(username).isEmpty()) {
            throw new WrongArgumentException("user.not.found.modify.failed");
        }
        long updatedAt = System.currentTimeMillis();
        databaseUserRepository.updateMysqlUserName(password, username);
        databaseRepository.refreshPermission();
        databaseUserRepository.updatePasswordAndDesc(username, password, desc, updatedAt);
        return databaseUserRepository.queryByUsername(username).orElse(null);
    }

    @Override
    @Transactional
    public void deleteAllByNames(List<String> usernames) {
        List<String> existing = databaseUserRepository.allUsernames();
        for (String username : usernames) {
            if (existing.contains(username)) {
                databaseUserRepository.deleteByUsername(username);
                databaseRepository.dropMysqlUser(username);
                continue;
            }
            throw new WrongArgumentException("user.not.found.cannot.delete");
        }
    }

    @Override
    public void revokeAuthorization(String databaseName, String databaseUser) {
        if (databaseUserRepository.queryByUsername(databaseUser).isEmpty()) {
            throw new WrongArgumentException("user.not.found.modify.failed");
        }
        boolean existDatabase = databaseRepository.showDatabases().stream()
                .anyMatch(s -> s.equals(databaseName));
        if (!existDatabase) {
            throw new WrongArgumentException("database.not.found", databaseName);
        }
        List<String> dbBelongUser = databaseRepository.databaseBelongUsers(databaseName);
        if (dbBelongUser.isEmpty()) {
            throw new WrongArgumentException("database.not.set.user.access.authority", databaseName);
        }
        if (!dbBelongUser.contains(databaseUser)) {
            throw new WrongArgumentException("database.user.not.access.authority", databaseUser);
        }
        databaseUserRepository.revokeAuthorization(databaseName, databaseUser);
    }
}
