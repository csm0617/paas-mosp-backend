package com.csm.paas.core.domain.software.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 软件-服务-端口 域的只读聚合结果。 */
@Data
@Builder
public class ServiceListEntity {
    private Integer runningDeployment;
    private Integer total;
    private Integer count;
    private List<ServiceRuntimeEntity> services;
}
