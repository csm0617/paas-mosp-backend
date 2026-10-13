package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

@Data
public class SettingPO {
    private String key;
    private String val;
    private String desc;
    private Long updatedAt;
    private Long createdAt;
}
