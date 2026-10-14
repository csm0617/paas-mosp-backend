package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_database_record` 行。 */
@Data
public class DatabaseRecordPO {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private String databaseName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
}
