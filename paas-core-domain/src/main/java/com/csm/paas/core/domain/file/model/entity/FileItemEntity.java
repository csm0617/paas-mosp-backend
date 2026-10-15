package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件列表项（厂商 {@code com.glriverside.gaopfdfs.response.FileItem}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/FileItem.java:6-16}。
 *
 * <p>与 {@link FileEntity} 的差别：多一个 {@code projectName}（按 {@code file_project_id}
 * 反查 {@code g_file_project.name} 回填），少一个 {@code deleted}（列表不返回该列）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileItemEntity {

    private String id;
    private String name;
    private Long currentSize;
    private Long size;
    private String md5;
    private String fileProjectId;
    /** 业务软件名称，反查不到时为 null（厂商不抛异常，保持 null）。 */
    private String projectName;
    private String path;
    private Long createdAt;
    private Long updatedAt;
}
