package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_software` 行（22 列）。 */
@Data
public class SoftwarePO {

    private String id;
    private String key;
    private String name;
    private String appName;
    private String routerKey;
    private String type;
    private String version;
    private Long createdAt;
    private Long updatedAt;
    private String framework;
    private String execName;
    private String location;
    private String remoteIp;
    private String remoteName;
    private String remotePassword;
    private Long activeCreate;
    private Long activeExpire;
    private Integer orderTop;
    private Long orderTime;
    private String desc;
    private String activeCode;
    private String screenshot;
    private String icon;
}
