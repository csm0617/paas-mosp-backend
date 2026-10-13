package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_upload_store_record` 行（7 列）。 */
@Data
public class UploadStoreRecordPO {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
    private String relationSoftwareId;
}
