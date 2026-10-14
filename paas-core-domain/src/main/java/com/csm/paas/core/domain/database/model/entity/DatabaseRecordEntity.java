package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库导入/导出记录（厂商 `model/DatabaseRecord`，表 `g_database_record`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseRecordEntity {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private String databaseName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
}
