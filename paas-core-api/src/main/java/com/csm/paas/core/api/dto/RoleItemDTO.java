package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 GET /v1/role 与 /v1/role/list → data.list[] 的单元素形状。
 * <p>键名逐字对齐运行时样本；`onLineUserList` 原型恒为 null（厂商取自 Redis 在线会话）。
 */
@Data
@Builder
public class RoleItemDTO {
    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private Long updatedAt;
    private Long createdAt;
    private String desc;
    private Integer type;
    private Object onLineUserList;
    private List<RoleSoftwareDTO> softwareList;
    /** 厂商侧由其它来源填充，当前数据恒为 null。 */
    private Object userList;
    private Object subRoleList;
    /** 由战位 key 推导（admin/dev）——照抄厂商语义。 */
    private Boolean adminOrDev;
    private Boolean admin;
    private Boolean dev;
}
