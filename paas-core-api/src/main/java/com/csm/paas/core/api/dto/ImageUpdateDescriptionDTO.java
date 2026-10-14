package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `third/request/harbor/repository/UpdateRepositoriesBody`
 * （`PUT /v1/image` 的请求体，**只有**一个 `description` 字段）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageUpdateDescriptionDTO {

    /** 镜像描述；长度 > 36 时厂商抛 `image.description.illegal`。 */
    private String description;
}
