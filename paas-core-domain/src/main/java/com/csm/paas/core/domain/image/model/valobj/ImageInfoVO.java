package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务关联的镜像（厂商 `controller/response/ImageInfo`：`{group, imageName, tag}`）。
 *
 * <p>厂商把它放进 `LinkedHashSet` 去重，依赖 `equals`/`hashCode`（三者相等即相等）——
 * 域服务里用 `LinkedHashSet<ImageInfoVO>` 时需要 Lombok 生成的 `equals`/`hashCode`（`@Data` 已提供）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageInfoVO {

    private String group;
    private String imageName;
    private String tag;
}
