package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 新增软件的入参（厂商 `SoftwareForm`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareFormVO {

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
    /** **战位 key** 列表（不是 id）。 */
    private List<String> roleListKey;
}
