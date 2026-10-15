package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FastDFS 存储节点信息（厂商 {@code com.glriverside.gaopfdfs.response.Storage}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/Storage.java:6-21}。
 *
 * <p>照抄点：{@code totalSize}/{@code freeSize} 是原始 **long**（MB 单位，见厂商
 * {@code gaop-be/.../controller/response/file/Storage.java} 的 Schema 描述），
 * 与 MinIO 版不同；{@code percentMemoryLeft} 仍是 Double。
 * 状态码语义：0 INIT / 1 WAIT_SYNC / 2 SYNCING / 3 IP_CHANGED / 4 DELETED / 5 OFFLINE /
 * 6 ONLINE / 7 ACTIVE / 99 NONE。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FdfsStorageInfoDTO {

    private String ip;
    private long totalSize;
    private long freeSize;
    private Double percentMemoryLeft;
    private int status;
    private String group;
}
