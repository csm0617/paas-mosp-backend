package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 ServicesItem.serviceAuth 的键名（逐字对齐运行时样本）。 */
@Data
@Builder
public class ServiceAuthDTO {
    private String serviceId;
    private Integer auth;
    private Integer authType;
    private Long authTime;
    private Long authInterval;
}
