package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /settings → data[] 的单元素形状：{key, updatedAt, createdAt, val, desc}。 */
@Data
@Builder
public class SettingDTO {
    private String key;
    private Long updatedAt;
    private Long createdAt;
    private String val;
    private String desc;
}
