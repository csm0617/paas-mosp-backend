package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MinIO 存储节点信息（厂商 {@code com.glriverside.gaopfdfs.response.StorageMinio}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/StorageMinio.java:6-30}。
 *
 * <p>照抄点：{@code totalSize}/{@code freeSize}/{@code percentMemoryLeft} 是 **Double**
 * （对比 FDFS 那条的 long），所以 JSON 里会出现 {@code 0.0} 这种形态，前端按浮点解析。
 * 状态常量沿用厂商 FDFS 的一套：0/1/2/3/4/5/6/7/99，MinIO 只用到 7（online）与构造时的 5。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MinioStorageInfoDTO {

    private String ip;
    private Double totalSize;
    private Double freeSize;
    private Double percentMemoryLeft;
    private int status;
    private String group;
}
