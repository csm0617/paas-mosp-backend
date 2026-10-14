package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 镜像环境变量（厂商 `controller/response/image/ImageEnvInfo`：`{name, value}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageEnvInfoVO {

    private String name;
    private String value;
}
