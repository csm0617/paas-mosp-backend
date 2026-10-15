package com.csm.paas.core.domain.file.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FastDFS 存储节点信息（厂商 {@code com.glriverside.gaopfdfs.response.Storage}
 * 与 {@code com.glriverside.gaop.be.controller.response.file.Storage}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/Storage.java:16-21}
 * 与 {@code recovered/gaop-be/.../service/FileService.getFdfsStatusInfos:323-366}。
 *
 * <p>照抄点：大小字段是 long（FDFS 版），单位为 MB；取不到存储信息时厂商会给每个配置的
 * tracker 造一条 {@code status=99(NONE)}、大小 0、{@code group="group1"} 的占位记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FdfsStorageNodeVO {

    private String ip;
    private long totalSize;
    private long freeSize;
    private Double percentMemoryLeft;
    private int status;
    private String group;
}
