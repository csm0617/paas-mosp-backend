package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;
import com.csm.paas.core.infrastructure.dao.IDatabaseDao;
import com.csm.paas.core.infrastructure.dao.po.DatabaseRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 数据库域仓储实现（只调 dao）。 */
@Repository
@RequiredArgsConstructor
public class DatabaseRepository implements IDatabaseRepository {

    private final IDatabaseDao databaseDao;

    @Override
    public List<DatabaseRecordEntity> queryRecordPage(int offset, int size) {
        return databaseDao.selectRecordPage(offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long countRecords() {
        return databaseDao.selectRecordCount();
    }

    private DatabaseRecordEntity toEntity(DatabaseRecordPO po) {
        return DatabaseRecordEntity.builder()
                .id(po.getId()).realStoreFilename(po.getRealStoreFilename())
                .execFileName(po.getExecFileName()).databaseName(po.getDatabaseName())
                .updatedAt(po.getUpdatedAt()).execFaultMessage(po.getExecFaultMessage())
                .status(po.getStatus())
                .build();
    }
}
