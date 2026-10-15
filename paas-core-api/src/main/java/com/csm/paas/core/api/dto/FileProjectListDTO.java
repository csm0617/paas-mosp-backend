package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 业务软件列表响应体（厂商 {@code com.glriverside.gaop.be.controller.response.file.project.FileProjectList}）。
 *
 * <p>厂商出处：
 * {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/response/file/project/FileProjectList.java:9-11}。
 *
 * <p>照抄点：键名是 {@code list}（不是文件列表那套的 {@code fileItems}），
 * {@code total} 来自 PageHelper 的 {@code Page.getTotal()}，与 where 一致（{@code deleted=false} + name like）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectListDTO {

    private List<FileProjectDTO> list;
    private Long total;
}
