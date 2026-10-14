package com.csm.paas.core.domain.software.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * 镜像存储空间（厂商 `controller/response/StorageInfo.java`）：`{free, total}`，单位**字节**。
 *
 * <p>只在 `GET /v1/service/imageStorage` 用；由 `df -h <mount>` 的 `Total`/`Avail` 两列换算而来。
 */
@Data
@Builder
public class StorageInfoVO {

    private long free;
    private long total;
}
