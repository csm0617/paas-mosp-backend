package com.csm.paas.core.domain.role.model.entity;

import lombok.Builder;
import lombok.Data;

/** 战位下的软件条目（厂商表 g_role_software）。 */
@Data
@Builder
public class RoleSoftwareEntity {
    private Long id;
    private String roleId;
    private String appId;
    private String appName;
    private String url;
    private Long updatedAt;
    private Long createdAt;
}
