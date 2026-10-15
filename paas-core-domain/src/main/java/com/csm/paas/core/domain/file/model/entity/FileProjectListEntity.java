package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 业务软件列表的分页结果（厂商 {@code FileProjectList}）。
 *
 * <p>厂商出处：
 * {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/response/file/project/FileProjectList.java:9-11}
 * 与 {@code FileProjectService.java:34-42}（PageHelper 的 {@code total}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectListEntity {

    private List<FileProjectEntity> list;
    private Long total;
}
