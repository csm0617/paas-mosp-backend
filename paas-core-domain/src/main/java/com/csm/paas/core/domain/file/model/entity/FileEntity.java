package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一行 {@code g_file}（厂商 {@code com.glriverside.gaopfdfs.model.File} 与
 * gaop-be 侧 {@code com.glriverside.gaop.be.model.File}，两份同构）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/model/File.java:6-16}；
 * 列映射见 {@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/dao/FileMapper.java:44}
 * （{@code id,name,file_project_id,path,created_at,updated_at,deleted,current_size,size,md5}）。
 *
 * <p>照抄点：{@code path} 是**对象存储里的 objectName**（MinIO 分支 = {@code UUID + 扩展名}，
 * FastDFS 分支 = {@code group/path}），不是本地路径；{@code currentSize} 只在分片上传里用，
 * 一次性上传时等于 {@code size}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileEntity {

    private String id;
    private String name;
    private String fileProjectId;
    /** 对象存储 objectName（MinIO：{@code UUID+扩展名}；FastDFS：{@code group/path}）。 */
    private String path;
    private Long createdAt;
    private Long updatedAt;
    private Boolean deleted;
    private Long currentSize;
    private Long size;
    private String md5;
}
