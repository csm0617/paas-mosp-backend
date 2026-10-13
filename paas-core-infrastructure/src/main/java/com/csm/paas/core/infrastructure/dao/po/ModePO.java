package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class ModePO {
    private Long id;
    private Integer type;
    private String mode;
    private String modeName;
    private Integer used;
    private Long createTime;
}
