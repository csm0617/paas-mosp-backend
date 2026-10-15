package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一行 {@code g_file_project}（业务软件）。
 *
 * <p>厂商出处：{@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/model/FileProject.java:6-12}；
 * 列映射见 {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/dao/FileProjectDynamicSqlSupport.java:20-24}
 * （{@code id,name,created_at,updated_at,deleted}）。
 *
 * <p>照抄点：删除是**物理删除**（{@code deleteByPrimaryKey}），{@code deleted} 列只被用来过滤，
 * 删除时不会置 true（{@code FileProjectService.java:69-75}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectEntity {

    private String id;
    private String name;
    private Long createdAt;
    private Long updatedAt;
    private Boolean deleted;
}
