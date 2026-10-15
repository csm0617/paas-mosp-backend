package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一行 {@code g_file}（厂商 {@code com.glriverside.gaopfdfs.model.File}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/model/File.java:6-16}。
 *
 * <p>上传端点直接把它当响应 {@code data} 返回（{@code PublicMinioController.java:75-80}、
 * {@code PublicFdfsController.java:61-67}），所以字段名/可空性必须逐字一致：
 * 十列 id/name/fileProjectId/path/createdAt/updatedAt/deleted/currentSize/size/md5。
 *
 * <p>照抄点：厂商两个上传实现都**不设置 {@code deleted}**（沿用库默认值），
 * 插入后再按 {@code path} 反查整行返回，所以响应里的 {@code deleted} 是库里读回来的值。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileInfoDTO {

    private String id;
    private String name;
    private String fileProjectId;
    private String path;
    private Long createdAt;
    private Long updatedAt;
    private Boolean deleted;
    private Long currentSize;
    private Long size;
    private String md5;
}
