package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `ServicePortForm`（`POST /v1/software/servicePort` 的请求体元素）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePortFormDTO {

    private Integer containerPort;
    private String serviceName;
}
