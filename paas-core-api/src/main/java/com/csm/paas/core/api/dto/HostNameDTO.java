package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `PUT /v1/service/hostName` 的请求体（`HostNameForm`）：`{serviceName, hostName}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HostNameDTO {

    private String serviceName;
    private String hostName;
}
