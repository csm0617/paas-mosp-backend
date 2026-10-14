package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 厂商 `GET /v1/service/imageStorage` 的响应（厂商 `controller/response/StorageInfo`）：
 * `{"free": <字节>, "total": <字节>}`。
 *
 * <p>两个字段都是 **long**（厂商声明为 `long`，取不到时是 `0` 而不是 null）。
 */
@Data
@Builder
public class StorageInfoDTO {

    private long free;
    private long total;
}
