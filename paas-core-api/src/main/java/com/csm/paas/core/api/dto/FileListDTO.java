package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件列表响应体（厂商 {@code com.glriverside.gaopfdfs.response.FileList}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/response/FileList.java:9-11}
 * （两个字段：{@code fileItems}、{@code total}）。
 *
 * <p>照抄点：{@code total} 与 {@code fileItems} **不是同一套条件**——厂商 {@code countFileSize}
 * 只按 {@code file_project_id} 计数，忽略时间范围与关键字（{@code FileMapper.java:129}），
 * 所以带 keyword 时 {@code total} 会大于实际返回条数。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileListDTO {

    private List<FileItemDTO> fileItems;
    private Long total;
}
