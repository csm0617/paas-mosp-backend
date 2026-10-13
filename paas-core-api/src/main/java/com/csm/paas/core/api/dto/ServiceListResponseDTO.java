package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 厂商 GET /v1/service 的 data 形状（键名逐字对齐运行时样本）。 */
@Data
@Builder
public class ServiceListResponseDTO {
    private Integer runningDeployment;
    private Integer total;
    private Integer count;
    private List<ServiceItemDTO> services;
}
