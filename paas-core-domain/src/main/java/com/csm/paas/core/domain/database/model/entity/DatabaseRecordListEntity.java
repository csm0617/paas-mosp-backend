package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 数据库记录列表（厂商 `DatabaseRecordList`：`{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseRecordListEntity {

    private List<DatabaseRecordEntity> list;
    private long total;
}
