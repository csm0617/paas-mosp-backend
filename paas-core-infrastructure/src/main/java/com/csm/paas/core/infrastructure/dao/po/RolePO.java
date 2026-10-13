package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class RolePO {
    private String id;
    private String key;
    private String name;
    private String desc;
    private Integer type;
    private Long updatedAt;
    private Long createdAt;
}
