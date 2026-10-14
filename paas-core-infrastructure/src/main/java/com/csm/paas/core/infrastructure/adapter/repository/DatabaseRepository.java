package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.model.entity.BusinessDatabaseNameEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseEntity;
import com.csm.paas.core.domain.database.model.entity.DatabasePermissionEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;
import com.csm.paas.core.domain.database.model.entity.DatabaseTableSizeItemEntity;
import com.csm.paas.core.infrastructure.dao.IDatabaseDao;
import com.csm.paas.core.infrastructure.dao.po.DatabaseRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 数据库域仓储实现（**只调 dao**）。 */
@Repository
@RequiredArgsConstructor
public class DatabaseRepository implements IDatabaseRepository {

    private final IDatabaseDao databaseDao;

    // ---------------------------------------------------------------- g_database_record

    @Override
    public List<DatabaseRecordEntity> queryRecordPage(int offset, int size) {
        return databaseDao.selectRecordPage(offset, size).stream().map(DatabaseRepository::toEntity).toList();
    }

    @Override
    public long countRecords() {
        return databaseDao.selectRecordCount();
    }

    @Override
    public void insertRecord(DatabaseRecordEntity record) {
        DatabaseRecordPO po = new DatabaseRecordPO();
        po.setId(record.getId());
        po.setRealStoreFilename(record.getRealStoreFilename());
        po.setExecFileName(record.getExecFileName());
        po.setDatabaseName(record.getDatabaseName());
        po.setUpdatedAt(record.getUpdatedAt());
        po.setExecFaultMessage(record.getExecFaultMessage());
        po.setStatus(record.getStatus());
        databaseDao.insertRecord(po);
    }

    // ---------------------------------------------------------------- g_database

    @Override
    public List<DatabaseEntity> queryDatabaseRows(String databaseName) {
        return databaseDao.selectDatabaseRows(databaseName).stream().map(DatabaseRepository::toEntity).toList();
    }

    @Override
    public Optional<DatabaseEntity> queryDatabaseByName(String databaseName) {
        return databaseDao.selectDatabaseByName(databaseName).map(DatabaseRepository::toEntity);
    }

    @Override
    public List<DatabaseEntity> queryAllDatabaseRows() {
        return databaseDao.selectAllDatabaseRows().stream().map(DatabaseRepository::toEntity).toList();
    }

    @Override
    public void insertDatabaseInfo(String databaseName, String service, String desc) {
        databaseDao.insertDatabaseInfo(databaseName, service, desc);
    }

    @Override
    public void insertDatabaseSelective(DatabaseEntity database) {
        databaseDao.insertDatabaseSelective(toRow(database));
    }

    @Override
    public void updateDatabaseByPrimaryKey(DatabaseEntity database) {
        databaseDao.updateDatabaseByPrimaryKey(toRow(database));
    }

    @Override
    public void updateDatabaseByPrimaryKeySelective(DatabaseEntity database) {
        databaseDao.updateDatabaseByPrimaryKeySelective(toRow(database));
    }

    @Override
    public void updateDatabaseDescription(String databaseName, String service, String desc) {
        databaseDao.updateDatabaseDescription(databaseName, service, desc);
    }

    @Override
    public void updateDatabaseLastUser(String databaseName, String userName) {
        databaseDao.updateDatabaseLastUser(databaseName, userName);
    }

    @Override
    public void deleteDatabaseRow(String databaseName) {
        databaseDao.deleteDatabaseRow(databaseName);
    }

    @Override
    public void deleteDatabaseRowsByName(String databaseName) {
        databaseDao.deleteDatabaseRowsByName(databaseName);
    }

    // ---------------------------------------------------------------- MySQL 管理连接

    @Override
    public List<String> showDatabases() {
        return databaseDao.showDataBases();
    }

    @Override
    public List<BusinessDatabaseNameEntity> allDatabasesTableCount(String keywordOrNull) {
        return databaseDao.allDatabasesTableCount(keywordOrNull).stream()
                .map(r -> BusinessDatabaseNameEntity.builder().name(r.getName()).tableCount(r.getTableCount()).build())
                .toList();
    }

    @Override
    public void createDatabase(String quotedDatabaseName) {
        databaseDao.createDatabase(quotedDatabaseName);
    }

    @Override
    public void dropDatabase(String databaseName) {
        databaseDao.dropDatabase(databaseName);
    }

    @Override
    public String queryAccessUser(String accessUser) {
        return databaseDao.queryAccessUser(accessUser);
    }

    @Override
    public List<DatabasePermissionEntity> databasesAccessControl(String databaseName, String accessUser) {
        return databaseDao.databasesAccessControl(databaseName, accessUser).stream()
                .map(r -> DatabasePermissionEntity.builder()
                        .databaseName(r.getDatabaseName()).accessUser(r.getAccessUser()).build())
                .toList();
    }

    @Override
    public List<String> databaseBelongUsers(String databaseName) {
        return databaseDao.databaseBelongUsers(databaseName);
    }

    @Override
    public List<String> mysqlUserList(String keywordOrNull) {
        return databaseDao.mysqlUserList(keywordOrNull);
    }

    @Override
    public int insertMysqlUser(String databaseUser, String password) {
        return databaseDao.insertMysqlUser(databaseUser, password);
    }

    @Override
    public void dropMysqlUser(String databaseUser) {
        databaseDao.deleteDatabaseAllUserByName(databaseUser);
    }

    @Override
    public void refreshPermission() {
        databaseDao.refreshPermission();
    }

    @Override
    public Map<String, String> allVariables() {
        Map<String, String> result = new LinkedHashMap<>();
        databaseDao.allVariables().forEach(v -> result.put(v.getVariableName(), v.getValue()));
        return result;
    }

    @Override
    public int getStorageSizeMb() {
        Integer size = databaseDao.getStorageSize();
        return size == null ? 0 : size;
    }

    @Override
    public String getMysqlCreatedAt() {
        return databaseDao.getMysqlCreatedAt();
    }

    @Override
    public List<DatabaseTableSizeItemEntity> getDbTableSize() {
        return databaseDao.getDbTableSize().stream()
                .map(r -> DatabaseTableSizeItemEntity.builder()
                        .databaseName(r.getDatabaseName()).tableName(r.getTableName()).size(r.getSize())
                        .build())
                .toList();
    }

    // ---------------------------------------------------------------- g_file

    @Override
    public long countFiles(String fileProjectId, String name) {
        return databaseDao.countFiles(fileProjectId, name);
    }

    @Override
    public Long queryFileUpdatedAt(String fileProjectId, String name) {
        return databaseDao.selectFileUpdatedAt(fileProjectId, name);
    }

    @Override
    public void insertFile(String id, String name, String fileProjectId, String path,
                           long createdAt, long updatedAt, boolean deleted, long size) {
        databaseDao.insertFile(id, name, fileProjectId, path, createdAt, updatedAt, deleted, size);
    }

    @Override
    public void updateFile(String fileProjectId, String name, String path, long size, long updatedAt) {
        databaseDao.updateFile(fileProjectId, name, path, size, updatedAt);
    }

    // ---------------------------------------------------------------- 映射

    private static DatabaseRecordEntity toEntity(DatabaseRecordPO po) {
        return DatabaseRecordEntity.builder()
                .id(po.getId()).realStoreFilename(po.getRealStoreFilename())
                .execFileName(po.getExecFileName()).databaseName(po.getDatabaseName())
                .updatedAt(po.getUpdatedAt()).execFaultMessage(po.getExecFaultMessage())
                .status(po.getStatus())
                .build();
    }

    private static DatabaseEntity toEntity(IDatabaseDao.DatabaseRow row) {
        return DatabaseEntity.builder()
                .id(row.getId()).databaseName(row.getDatabaseName()).username(row.getUsername())
                .desc(row.getDesc()).sqlName(row.getSqlName()).service(row.getService())
                .build();
    }

    private static IDatabaseDao.DatabaseRow toRow(DatabaseEntity e) {
        IDatabaseDao.DatabaseRow row = new IDatabaseDao.DatabaseRow();
        row.setId(e.getId());
        // 厂商 model/Database 的 setter 对每个字符串字段都 trim（DatabaseService 所有写入都走 setter）
        row.setDatabaseName(trim(e.getDatabaseName()));
        row.setUsername(trim(e.getUsername()));
        row.setDesc(trim(e.getDesc()));
        row.setSqlName(trim(e.getSqlName()));
        row.setService(trim(e.getService()));
        return row;
    }

    private static String trim(String raw) {
        return raw == null ? null : raw.trim();
    }
}
