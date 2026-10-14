package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库导入/导出记录（厂商 `model/DatabaseRecord`，7 键）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseRecordDTO {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private String databaseName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
}
