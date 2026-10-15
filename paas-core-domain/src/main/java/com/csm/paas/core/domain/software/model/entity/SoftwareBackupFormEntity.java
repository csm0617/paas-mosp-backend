package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个软件的导出形态（厂商 `controller/response/ExportSoftwareForm extends SoftwareForm`）。
 *
 * <p>字段顺序/名称与厂商 GSON 输出一致：先 `SoftwareForm` 的 16 个字段，再
 * `serviceList`/`webEntryName`/`webEntryPort`/`servicePortList`。此外本重写额外带
 * `nameUs`/`nameFr`（厂商 `SoftwareForm` 本来就有这两个字段，只是排在前 16 个里）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareBackupFormEntity {

    // ---- SoftwareForm 的字段（厂商 `controller/form/SoftwareForm`）----
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String type;
    private String routerKey;
    private String desc;
    private String location;
    private String remoteIp;
    private String remoteName;
    private String remotePassword;
    private String framework;
    private String execName;
    private String icon;
    private List<String> screenShotList;
    private List<String> roleListKey;

    // ---- ExportSoftwareForm 追加的字段 ----
    /** 该软件关联的服务名（厂商 `softwareRelationService` 的结果）。 */
    @Builder.Default
    private List<String> serviceList = new ArrayList<>();
    private String webEntryName;
    private Integer webEntryPort;
    private List<com.csm.paas.core.domain.software.model.valobj.SoftwareBackupPortVO> servicePortList;
}
