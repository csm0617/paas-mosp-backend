package com.csm.paas.core.domain.database.adapter.repository;

import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;

import java.util.List;

/** 数据库域仓储（本轮只做 `g_database_record`）。 */
public interface IDatabaseRepository {

    /** 全部记录，按 `updated_at DESC`（厂商 `DatabaseRecordService.getAllDatabaseRecord:25-33`）。 */
    List<DatabaseRecordEntity> queryRecordPage(int offset, int size);

    long countRecords();
}
