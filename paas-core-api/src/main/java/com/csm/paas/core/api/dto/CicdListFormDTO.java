package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/cicdList` 的请求体（厂商 `controller/form/cicd/CicdForm.java:8-15`）。
 *
 * <p>注意厂商这四个字段是**包级（default）可见性**，不是 `private`——Jackson 反射设置字段照样能反序列化。
 * 本仓用 `private` + Lombok（对外 JSON 契约不变）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdListFormDTO {

    private String userId;
    private Integer page;
    private Integer size;
    private String keyword;
}
