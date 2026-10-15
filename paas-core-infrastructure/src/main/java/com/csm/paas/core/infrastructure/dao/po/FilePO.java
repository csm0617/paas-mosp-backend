package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_file` 的投影（厂商 {@code com.glriverside.gaopfdfs.model.File}）。
 *
 * <p>十列，与厂商 {@code FileMapper} 的 resultMap 一一对应
 * （{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/dao/FileMapper.java:44}）：
 * {@code id,name,file_project_id,path,created_at,updated_at,deleted,current_size,size,md5}。
 *
 * <p>注意同表的另一次插入在 {@code IDatabaseDao#insertFile}（数据库快照路径，只写 8 列）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilePO {

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
