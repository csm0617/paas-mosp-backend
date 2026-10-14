package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Docker Registry 镜像条目（厂商 `controller/third/api/ImageItem`）。
 *
 * <p>由 `DockerRegistryApiService.doGetImageListByProject` 从 **Registry v2 HTTP API** 拼装：
 * `GET /v2/_catalog` → `GET /v2/{repo}/tags/list` → `GET /v2/{repo}/manifests/{tag}`
 * → `GET /v2/{repo}/blobs/{configDigest}`（取 `created`，并把各层 `size` 求和）。
 *
 * <p>厂商的 `ImageItem` 还有 `parentId`/`repoDigests`/`labels`/`containers` 四个字段，
 * 但**从未赋值**，属于死字段，这里不放（放了也永远为 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageItemVO {

    /** 厂商 `DateTimeUtils.convertDateTime(jsonBlobs.getCreated())`：`OffsetDateTime.parse` 失败/null ⇒ null。 */
    private Long created;
    /** 厂商：`imageName + ":" + tag`。 */
    private String id;
    /** 厂商：`[registryHost去掉http(s):// + "/" + imageName + ":" + tag]`。 */
    private String[] repoTags;
    /** 厂商：各层 size 之和。 */
    private Long size;
    /** 厂商：等于 {@link #size}。 */
    private Long virtualSize;
    /** 厂商：写死 `-1L`。 */
    private Long sharedSize;
}
