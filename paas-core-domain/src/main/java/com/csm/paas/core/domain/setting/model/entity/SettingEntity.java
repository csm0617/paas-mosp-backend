package com.csm.paas.core.domain.setting.model.entity;

import lombok.Builder;
import lombok.Data;

/** 系统设置（表 g_setting，主键是 `key`）。 */
@Data
@Builder
public class SettingEntity {
    private String key;
    private String val;
    private String desc;
    private Long updatedAt;
    private Long createdAt;
}
