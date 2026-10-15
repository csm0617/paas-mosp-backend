package com.csm.paas.core.domain.file.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件列表的分页结果（厂商把 {@code FileList} 直接当 service 返回值）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/FileList.java:9-11}
 * 与 {@code FileMinioService.java:87-105} / {@code FileService.java:351-370}。
 *
 * <p>照抄点：{@code total} 与 {@code fileItems} 的条件**不一致**——{@code countFileSize}
 * 只按 {@code file_project_id} 计数（忽略 start/end/keyword），所以带关键字时 total 可能偏大。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileListEntity {

    private List<FileItemEntity> fileItems;
    private Long total;
}
