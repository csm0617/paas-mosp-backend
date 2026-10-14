package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** `g_service_port` 行（8 列）。 */
@Data
public class ServicePortPO {

    private Long id;
    private String softwareId;
    private String serviceName;
    private Integer port;
    private String pathName;
    private Boolean webEntry;
    private Boolean allow;
    private Long updatedAt;
}
