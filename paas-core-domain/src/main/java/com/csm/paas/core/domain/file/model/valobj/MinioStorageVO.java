package com.csm.paas.core.domain.file.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MinIO 存储节点信息（厂商 {@code com.glriverside.gaopfdfs.response.StorageMinio}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/StorageMinio.java:16-21}
 * 与 {@code FileMinioService.getStorageInfo:404-429}。
 *
 * <p>照抄点：三个大小字段是 Double（MinIO 版），状态码复用厂商 FDFS 的一套常量
 * （online ⇒ 7，其余保持构造时的 5 = OFFLINE）；{@code group} 恒为 {@code "minio"}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MinioStorageVO {

    private String ip;
    private Double totalSize;
    private Double freeSize;
    private Double percentMemoryLeft;
    private int status;
    private String group;
}
