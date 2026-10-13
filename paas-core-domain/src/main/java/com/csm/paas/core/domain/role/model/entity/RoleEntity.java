package com.csm.paas.core.domain.role.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 战位（厂商 UI 叫"门户/席位"，表注释写"战位表"）。 */
@Data
@Builder
public class RoleEntity {
    private String id;
    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String desc;
    private Integer type;
    private Long updatedAt;
    private Long createdAt;
    private List<RoleSoftwareEntity> softwareList;
    /** 由 key 推导：admin/dev/adminOrDev（厂商语义）。 */
    private Boolean admin;
    private Boolean dev;
    private Boolean adminOrDev;
}
