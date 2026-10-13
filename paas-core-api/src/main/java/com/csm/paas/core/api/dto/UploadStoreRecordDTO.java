package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `UploadStoreRecord`（7 键）：软件当前版本对应的上传记录。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadStoreRecordDTO {

    private String id;
    private String realStoreFilename;
    private String execFileName;
    private Long updatedAt;
    private String execFaultMessage;
    private Integer status;
    private String relationSoftwareId;
}
