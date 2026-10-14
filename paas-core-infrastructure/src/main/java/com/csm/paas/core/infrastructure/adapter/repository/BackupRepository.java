package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.backup.adapter.repository.IBackupRepository;
import com.csm.paas.core.domain.backup.model.entity.BackupEntity;
import com.csm.paas.core.domain.backup.model.entity.BackupSettingEntity;
import com.csm.paas.core.infrastructure.dao.IBackupDao;
import com.csm.paas.core.infrastructure.dao.po.BackupPO;
import com.csm.paas.core.infrastructure.dao.po.BackupSettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 备份域仓储实现（**只调 dao**）。 */
@Repository
@RequiredArgsConstructor
public class BackupRepository implements IBackupRepository {

    private final IBackupDao backupDao;

    @Override
    public List<BackupEntity> queryBackupPage(String likeKeywordOrNull, int offset, int size) {
        return backupDao.selectBackupPage(likeKeywordOrNull, offset, size).stream().map(BackupRepository::toEntity).toList();
    }

    @Override
    public long countBackups(String likeKeywordOrNull) {
        return backupDao.countBackups(likeKeywordOrNull);
    }

    @Override
    public Optional<BackupEntity> queryByKey(String databaseKey) {
        return backupDao.selectByKey(databaseKey).map(BackupRepository::toEntity);
    }

    @Override
    public void updateByPrimaryKey(BackupEntity backup) {
        backupDao.updateByPrimaryKey(toPO(backup));
    }

    @Override
    public void updateByPrimaryKeySelective(BackupEntity backup) {
        backupDao.updateByPrimaryKeySelective(toPO(backup));
    }

    @Override
    public List<BackupSettingEntity> queryAllSettings() {
        return backupDao.selectAllSettings().stream().map(BackupRepository::toEntity).toList();
    }

    @Override
    public List<BackupSettingEntity> querySettingPage(int offset, int size) {
        return backupDao.selectSettingPage(offset, size).stream().map(BackupRepository::toEntity).toList();
    }

    @Override
    public long countSettings() {
        return backupDao.countSettings();
    }

    @Override
    public Optional<BackupSettingEntity> querySettingByName(String databaseName) {
        return backupDao.selectSettingByName(databaseName).map(BackupRepository::toEntity);
    }

    @Override
    public void insertSetting(String databaseName, Integer type) {
        backupDao.insertSetting(databaseName, type);
    }

    @Override
    public void deleteSetting(String databaseName) {
        backupDao.deleteSetting(databaseName);
    }

    @Override
    public boolean updateSettingType(String databaseName, Integer type) {
        return backupDao.updateSettingType(databaseName, type) > 0;
    }

    @Override
    public void deleteAllSettings() {
        backupDao.deleteAllSettings();
    }

    private static BackupEntity toEntity(BackupPO po) {
        return BackupEntity.builder()
                .id(po.getId()).databaseKey(po.getDatabaseKey()).databaseName(po.getDatabaseName())
                .databaseFilename(po.getDatabaseFilename()).size(po.getSize())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt()).msg(po.getMsg())
                .build();
    }

    private static BackupPO toPO(BackupEntity e) {
        BackupPO po = new BackupPO();
        po.setId(e.getId());
        po.setDatabaseKey(e.getDatabaseKey());
        po.setDatabaseName(e.getDatabaseName());
        po.setDatabaseFilename(e.getDatabaseFilename());
        po.setSize(e.getSize());
        po.setCreatedAt(e.getCreatedAt());
        po.setUpdatedAt(e.getUpdatedAt());
        po.setMsg(e.getMsg());
        return po;
    }

    private static BackupSettingEntity toEntity(BackupSettingPO po) {
        return BackupSettingEntity.builder()
                .id(po.getId()).databaseName(po.getDatabaseName()).type(po.getType())
                .build();
    }
}
