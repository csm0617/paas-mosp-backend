package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 软件（表 `g_software`，**24 列**）。
 * <p>字段与声明顺序照抄厂商 `model/Software.java`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareEntity {

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
