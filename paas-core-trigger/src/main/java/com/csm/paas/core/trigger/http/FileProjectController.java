package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.FileProjectDTO;
import com.csm.paas.core.api.dto.FileProjectFormDTO;
import com.csm.paas.core.api.dto.FileProjectListDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.file.model.entity.FileProjectEntity;
import com.csm.paas.core.domain.file.model.entity.FileProjectListEntity;
import com.csm.paas.core.domain.file.service.IFileProjectService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务软件（文件项目）端点，表 {@code g_file_project}。
 *
 * <p>厂商出处：{@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/FileProjectController.java}
 * （类级前缀 {@code /v1/pub/file_project}）：
 * <ul>
 *   <li>列表：类级 {@code @GetMapping}（:36-43，入参 page/size/keyword）；</li>
 *   <li>新增：类级 {@code @PostMapping}（:45-50，body 只有 name）；</li>
 *   <li>修改：{@code PUT /{projectId}}（:52-57）；</li>
 *   <li>删除：{@code DELETE /{projectId}}（:59-64，恒返回 {@code data:true}）。</li>
 * </ul>
 *
 * <p>照抄点：
 * <ul>
 *   <li>列表与新增共用一个 URL（只有方法不同），所以这里有**两个**类级路径映射；</li>
 *   <li>删除接口即使成功也只回 {@code true}（厂商没把删除结果放进去）；</li>
 *   <li>分页归一化用厂商 {@code SqlUtils} 语义（见 {@link PageUtils}）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/pub/file_project")
@RequiredArgsConstructor
public class FileProjectController {

    private final IFileProjectService fileProjectService;

    /** 厂商类级 {@code GET}（{@code FileProjectController.java:36-43}）。 */
    @GetMapping
    public ResponseDTO<FileProjectListDTO> getFileProject(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        FileProjectListEntity result = fileProjectService.queryFileProjectList(
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        List<FileProjectDTO> list = result.getList().stream()
                .map(FileProjectController::toDTO)
                .toList();
        return ResponseDTO.ok(FileProjectListDTO.builder().list(list).total(result.getTotal()).build());
    }

    /** 厂商类级 {@code POST}（{@code FileProjectController.java:45-50}）。 */
    @PostMapping
    public ResponseDTO<FileProjectDTO> addFileProject(@RequestBody FileProjectFormDTO form) {
        return ResponseDTO.ok(toDTO(fileProjectService.addFileProject(form.getName())));
    }

    /** 厂商 {@code PUT /{projectId}}（{@code FileProjectController.java:52-57}）。 */
    @PutMapping("/{projectId}")
    public ResponseDTO<FileProjectDTO> updateFileProject(@PathVariable String projectId,
                                                         @RequestBody FileProjectFormDTO form) {
        return ResponseDTO.ok(toDTO(fileProjectService.updateFileProject(projectId, form.getName())));
    }

    /** 厂商 {@code DELETE /{projectId}}（{@code FileProjectController.java:59-64}）：恒回 {@code true}。 */
    @DeleteMapping("/{projectId}")
    public ResponseDTO<Boolean> removeFileProject(@PathVariable String projectId) {
        fileProjectService.removeFileProject(projectId);
        return ResponseDTO.ok(true);
    }

    private static FileProjectDTO toDTO(FileProjectEntity e) {
        return FileProjectDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .deleted(e.getDeleted())
                .build();
    }
}
