package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 内部健康检查响应（非厂商契约路径）。 */
@Data
@Builder
public class HealthResponseDTO {
    private String status;
    private String application;
    private String version;
}
