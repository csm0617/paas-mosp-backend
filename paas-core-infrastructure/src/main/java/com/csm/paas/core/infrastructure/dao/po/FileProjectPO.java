package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_file_project` 的投影（厂商 {@code com.glriverside.gaop.be.model.FileProject}）。
 *
 * <p>五列，与厂商 {@code FileProjectMapper} 的 resultMap 一一对应
 * （{@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/dao/FileProjectMapper.java}）：
 * {@code id,name,created_at,updated_at,deleted}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectPO {

    private String id;
    private String name;
    private Long createdAt;
    private Long updatedAt;
    private Boolean deleted;
}
