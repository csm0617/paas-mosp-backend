package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `PUT /v1/service/updateName` 的参数（都是 **query parameter**，不是 body）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceNameDTO {

    /** 服务标识（= Deployment/StatefulSet 名）。 */
    private String serviceKey;
    private String name;
    private String nameUs;
    private String nameFr;
}
