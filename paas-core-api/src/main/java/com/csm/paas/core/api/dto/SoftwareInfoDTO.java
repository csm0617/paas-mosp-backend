package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `GET /v1/software/{id}` 的 data（`SoftwareInfo`）。
 * <p>它是 **23 键的聚合**：`Software` 的可拷贝字段 + `roleKeyList`/`roleNameList`（关联战位）+
 * `nameUs`/`nameFr`（i18n 兜底成 name）+ `screenShotList`（`screenshot` 按 `,` 拆）+ `licence`（K3）+
 * `version`/`uploadStoreRecord`（上传记录）+ `status`/`serviceList`（K8s 现场态）。
 * <p>字段顺序照抄厂商声明顺序。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareInfoDTO {

    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String type;
    private String desc;
    private String routerKey;
    private String location;
    private String remoteIp;
    private String remoteName;
    private String remotePassword;
    private List<String> roleNameList;
    private List<String> roleKeyList;
    private String framework;
    private String execName;
    private String version;
    private UploadStoreRecordDTO uploadStoreRecord;
    /** 厂商默认值是字符串 `"STOP"`（字段初始化器），不是 null。 */
    private String status;
    private List<Object> serviceList;
    private SoftwareLicenceDTO licence;
    private String icon;
    private List<String> screenShotList;
}
