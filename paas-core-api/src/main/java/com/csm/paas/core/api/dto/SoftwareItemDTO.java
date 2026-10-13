package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `GET /v1/software` 列表项 = `Software` 的 24 列 + `SoftwareMessage` 追加的 4 键
 * （`releaseStatus`/`roleList`/`web`/`url`），共 **28 键**。
 * <p>字段顺序照抄厂商的声明顺序（父类在前、子类在后），Jackson 按声明顺序输出。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareItemDTO {

    // ---- Software（24）----
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

    // ---- SoftwareMessage 追加（4）----
    /** 是否已发布（白名单里有 `/<key>/**` → 1，否则 0）。 */
    private Integer releaseStatus;
    private List<RelationRoleDTO> roleList;
    /** 0=没有 web 入口；1=有入口且 Deployment 健康；2=有入口但找不到 Deployment；3=异常态。 */
    private Integer web;
    private String url;
}
