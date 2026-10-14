package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `ServicePort`（8 键）：软件在某服务下的端口/路径映射。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePortDTO {

    private Long id;
    private String softwareId;
    private String serviceName;
    private Integer port;
    private String pathName;
    private Boolean webEntry;
    private Boolean allow;
    private Long updatedAt;
}
