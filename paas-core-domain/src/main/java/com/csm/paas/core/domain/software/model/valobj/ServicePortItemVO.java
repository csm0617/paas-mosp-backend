package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `ServicePortForm`（`POST /v1/software/servicePort` 的请求体元素）：`{containerPort, serviceName}`。
 * <p>注意字段名是 **containerPort**，但写进 `g_service_port.port` 的就是它的值。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePortItemVO {

    private Integer containerPort;
    private String serviceName;
}
