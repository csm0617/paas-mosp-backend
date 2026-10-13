package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商战位项里 softwareList[] 的键名（逐字对齐运行时样本）。 */
@Data
@Builder
public class RoleSoftwareDTO {
    private Long id;
    private String roleId;
    private String appId;
    private String appName;
    private String url;
    private Long updatedAt;
    private Long createdAt;
    /** 厂商侧存在的额外键，当前数据恒为 null。 */
    private Integer order;
    private Object software;
    private Object icon;
    private Object screenShotList;
    private Object web;
}
