package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 要写进 K8s Service 的一个端口（厂商 `V1ServicePort` 里被赋值的 5-6 个字段）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePortSpecVO {

    private String appProtocol;
    /** `<protocol>-<服务名>-<下标>` 的**小写**形式。 */
    private String name;
    private Integer port;
    private String protocol;
    private Integer targetPort;
    private Integer nodePort;
}
