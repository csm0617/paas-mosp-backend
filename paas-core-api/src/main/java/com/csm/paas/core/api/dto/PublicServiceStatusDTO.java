package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/pub/service_status` 的响应体（厂商 `controller/response/ServiceStatusList.java:10-12`
 * 包着 `ServiceStatus{serviceName,status}`）。
 *
 * <p>照抄点：`status` 的取值里有一个 **`STARING`**（厂商把 STARTING 拼错，`TenxUtils.java:70`、`:74`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicServiceStatusDTO {

    private String serviceName;
    private String status;

    /** 厂商 `ServiceStatusList{List&lt;ServiceStatus&gt; service}`（字段名叫 `service`，不是 `list`）。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServiceStatusListDTO {

        private List<PublicServiceStatusDTO> service;
    }
}
