package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/updateTask` 的请求体（厂商 `controller/form/cicd/DockerFileForm.java:8-14`）。
 *
 * <p>字段：`id` / `desc` / `dockerfile`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdDockerFileDTO {

    private Integer id;
    private String desc;
    private String dockerfile;
}
