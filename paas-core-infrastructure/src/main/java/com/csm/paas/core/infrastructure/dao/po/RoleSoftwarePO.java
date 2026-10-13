package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class RoleSoftwarePO {
    private Long id;
    private String roleId;
    private String appId;
    private String appName;
    private String url;
    private Long updatedAt;
    private Long createdAt;
}
