package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deployment 容器里声明的端口（厂商推导 Service 端口时用的两个字段）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerPortVO {

    private String protocol;
    private Integer containerPort;
}
