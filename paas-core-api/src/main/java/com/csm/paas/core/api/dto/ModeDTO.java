package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/mode/current 的 data 形状：{id, type, mode, used, createTime, modeName}。 */
@Data
@Builder
public class ModeDTO {
    private Long id;
    private Integer type;
    private String mode;
    private Integer used;
    private Long createTime;
    private String modeName;
}
