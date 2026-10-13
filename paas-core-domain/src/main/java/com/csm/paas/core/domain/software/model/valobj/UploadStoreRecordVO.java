package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 上传记录（厂商 `UploadStoreRecord`，7 键）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadStoreRecordVO {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
    private String relationSoftwareId;
}
