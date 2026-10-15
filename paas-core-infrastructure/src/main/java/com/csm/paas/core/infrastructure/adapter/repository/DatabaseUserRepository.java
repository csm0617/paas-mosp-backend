package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.database.adapter.repository.IDatabaseUserRepository;
import com.csm.paas.core.domain.database.model.entity.DatabaseUserEntity;
import com.csm.paas.core.infrastructure.dao.IDatabaseUserDao;
import com.csm.paas.core.infrastructure.dao.po.DatabaseUserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 数据库用户仓储实现（**只调 dao**；MySQL 管理连接段与 `IDatabaseDao` 同口径，**类 C**）。 */
@Repository
@RequiredArgsConstructor
public class DatabaseUserRepository implements IDatabaseUserRepository {

    private final IDatabaseUserDao databaseUserDao;

    @Override
    public List<DatabaseUserEntity> queryPageByUsernames(List<String> usernames, int offset, int size) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        return databaseUserDao.selectPageByUsernames(usernames, offset, size).stream()
                .map(DatabaseUserRepository::toEntity).toList();
    }

    @Override
    public long countByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return 0L;
        }
        return databaseUserDao.countByUsernames(usernames);
    }

    @Override
    public Optional<DatabaseUserEntity> queryByUsername(String username) {
        return Optional.ofNullable(databaseUserDao.selectByUsername(username)).map(DatabaseUserRepository::toEntity);
    }

    @Override
    public void insert(DatabaseUserEntity user) {
        DatabaseUserPO po = new DatabaseUserPO();
        po.setUsername(user.getUsername());
        po.setPassword(user.getPassword());
        po.setDesc(user.getDesc());
        po.setCreatedAt(user.getCreatedAt());
        po.setUpdatedAt(user.getUpdatedAt());
        databaseUserDao.insert(po);
    }

    @Override
    public void updatePasswordAndDesc(String username, String password, String desc, long updatedAt) {
        databaseUserDao.updateByUsername(username, password, desc, updatedAt);
    }

    @Override
    public void deleteByUsername(String username) {
        databaseUserDao.deleteByUsername(username);
    }

    @Override
    public void deleteAll() {
        databaseUserDao.deleteAll();
    }

    @Override
    public List<String> allUsernames() {
        return databaseUserDao.allUsernames();
    }

    @Override
    public List<String> mysqlUserListByUserName(String userName) {
        return databaseUserDao.mysqlUserListByUserName(userName);
    }

    @Override
    public void updateMysqlUserName(String password, String databaseUser) {
        databaseUserDao.updateMysqlUserName(password, databaseUser);
    }

    @Override
    public void revokeAuthorization(String databaseName, String databaseUser) {
        databaseUserDao.revokeAuthorization(databaseName, databaseUser);
    }

    private static DatabaseUserEntity toEntity(DatabaseUserPO po) {
        return DatabaseUserEntity.builder()
                .id(po.getId()).username(po.getUsername()).password(po.getPassword())
                .desc(po.getDesc()).updatedAt(po.getUpdatedAt()).createdAt(po.getCreatedAt())
                .build();
    }
}
