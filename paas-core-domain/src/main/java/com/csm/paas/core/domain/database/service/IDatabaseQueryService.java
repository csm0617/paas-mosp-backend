package com.csm.paas.core.domain.database.service;

import com.csm.paas.core.domain.database.model.entity.DatabaseRecordListEntity;

/**
 * 数据库域查询（厂商 `DatabaseRecordController` → `DatabaseRecordService`）。
 *
 * <p>本轮只做 `GET /v1/database_record`（`updated_at DESC` 分页）。
 */
public interface IDatabaseQueryService {

    DatabaseRecordListEntity queryRecords(int page, int size);
}
