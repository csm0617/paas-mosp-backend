package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件列表项（厂商 {@code com.glriverside.gaopfdfs.response.FileItem}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/FileItem.java:6-16}
 * （字段顺序 id/name/currentSize/size/md5/fileProjectId/projectName/path/createdAt/updatedAt）。
 *
 * <p>照抄点：{@code projectName} 不在 {@code g_file} 里，是列表组装时按 {@code file_project_id}
 * 反查 {@code g_file_project.name} 回填的（{@code FileMinioService.java:91-99}、
 * {@code FileService.java:354-363}）；反查不到时保持 null。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileItemDTO {

    private String id;
    private String name;
    private Long currentSize;
    private Long size;
    private String md5;
    private String fileProjectId;
    /** 业务软件（{@code g_file_project.name}）名称，反查不到时为 null。 */
    private String projectName;
    private String path;
    private Long createdAt;
    private Long updatedAt;
}
