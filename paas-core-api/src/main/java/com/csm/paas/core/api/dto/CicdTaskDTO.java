package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `POST /v1/pub/task` 的请求体（厂商 `controller/form/cicd/TaskForm.java:8-19`）。
 *
 * <p>字段名必须与厂商一致：`userId` / `imageStorage` / `imageName` / **`desc`** / `dockerfile`
 * （注意是 `desc` 不是 `description`，而入库列叫 `description`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdTaskDTO {

    private String userId;
    private String imageStorage;
    private String imageName;
    private String desc;
    private String dockerfile;
}
