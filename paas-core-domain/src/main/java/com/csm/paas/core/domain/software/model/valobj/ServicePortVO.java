package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 软件端口映射（厂商 `ServicePort`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePortVO {

    private Long id;
    private String softwareId;
    private String serviceName;
    private Integer port;
    private String pathName;
    private Boolean webEntry;
    private Boolean allow;
    private Long updatedAt;
}
