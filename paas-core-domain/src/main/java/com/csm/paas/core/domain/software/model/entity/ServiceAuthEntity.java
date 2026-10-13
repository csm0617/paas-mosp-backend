package com.csm.paas.core.domain.software.model.entity;

import lombok.Builder;
import lombok.Data;

/** 服务的授权状态（对应厂商表 g_service_auth）。 */
@Data
@Builder
public class ServiceAuthEntity {
    private String serviceId;
    private Integer auth;
    private Integer authType;
    private Long authTime;
    private Long authInterval;
}
