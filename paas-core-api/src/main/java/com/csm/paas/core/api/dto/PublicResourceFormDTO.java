package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/patchResource` 的请求体（厂商 `controller/form/ResourceForm.java:8-12` +
 * `LimitInfo extends ResourceInfo`）。
 *
 * <p>契约形状（**字段顺序也被照抄**：Jackson 先出父类字段，所以 `limits` 是 `cpu,memory,gpu`）：
 * <pre>
 * {"name":"...", "limits":{"cpu":"1","memory":"1Gi","gpu":null}, "requests":{"cpu":"1","memory":"1Gi"}}
 * </pre>
 * 注意 `requests` **没有 gpu**（厂商 `ResourceInfo` 只有 cpu/memory）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicResourceFormDTO {

    private String name;
    private LimitDTO limits;
    private RequestDTO requests;

    /** 厂商 `LimitInfo extends ResourceInfo{String cpu; String memory;}` + `String gpu`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LimitDTO {

        private String cpu;
        private String memory;
        private String gpu;
    }

    /** 厂商 `ResourceInfo{String cpu; String memory;}`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestDTO {

        private String cpu;
        private String memory;
    }
}
