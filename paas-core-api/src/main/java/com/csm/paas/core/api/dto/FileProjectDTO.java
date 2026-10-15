package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务软件（文件项目）行（厂商 {@code com.glriverside.gaop.be.model.FileProject}，表 {@code g_file_project}）。
 *
 * <p>厂商出处：{@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/model/FileProject.java:6-12}
 * 与 {@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/model/FileProject.java:6-12}
 * （两份同构）。
 *
 * <p>五个字段：id/name/createdAt/updatedAt/deleted。厂商在 {@code addFileProject}/
 * {@code updateFileProject} 里都返回完整行，所以 {@code deleted} 也会出现在新增/修改的响应里。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectDTO {

    private String id;
    private String name;
    private Long createdAt;
    private Long updatedAt;
    private Boolean deleted;
}
