package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 镜像声明的端口（厂商 `controller/response/image/ExposedPortItem`：`{port, protocol}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExposedPortItemVO {

    private Integer port;
    private String protocol;
}
