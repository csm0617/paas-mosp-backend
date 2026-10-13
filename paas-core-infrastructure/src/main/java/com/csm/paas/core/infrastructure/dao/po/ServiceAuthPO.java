package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** g_service_auth 的行映射（键名用下划线，配合 map-underscore-to-camel-case）。 */
@Data
public class ServiceAuthPO {
    private String serviceId;
    private Integer auth;
    private Integer authType;
    private Long authTime;
    private Long authInterval;
}
