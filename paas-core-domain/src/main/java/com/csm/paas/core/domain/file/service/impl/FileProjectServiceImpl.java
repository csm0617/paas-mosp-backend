package com.csm.paas.core.domain.file.service.impl;

import com.csm.paas.core.domain.file.adapter.repository.IFileProjectRepository;
import com.csm.paas.core.domain.file.model.entity.FileProjectEntity;
import com.csm.paas.core.domain.file.model.entity.FileProjectListEntity;
import com.csm.paas.core.domain.file.service.IFileProjectService;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 业务软件（{@code g_file_project}）服务实现。
 *
 * <p>逐行照抄厂商 {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/service/FileProjectService.java}。
 * 厂商用 PageHelper，本仓用 offset/limit（{@link PageUtils#offset} 与 PageHelper 的
 * {@code (page-1)*size} 等价）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProjectServiceImpl implements IFileProjectService {

    private final IFileProjectRepository fileProjectRepository;

    /** 厂商 {@code list:34-42}。 */
    @Override
    public FileProjectListEntity queryFileProjectList(int page, int size, String keyword) {
        // 照抄：这里**没有** trim、也没有 isBlank——只有 null 才不过滤（对比文件列表会 trim）
        String likeKeyword = keyword == null ? null : "%" + keyword + "%";
        return FileProjectListEntity.builder()
                .list(fileProjectRepository.queryFileProjectPage(likeKeyword,
                        PageUtils.offset(page, size), size))
                .total(fileProjectRepository.countFileProjects(likeKeyword))
                .build();
    }

    /** 厂商 {@code addFileProject:44-55}。 */
    @Override
    public FileProjectEntity addFileProject(String name) {
        boolean exist = fileProjectRepository.queryByNameAndNotDeleted(name).isPresent();
        if (exist) {
            // 厂商把 name 当参数带出去：文案 "软件名称 {0} 已存在"
            throw new WrongArgumentException("software.name.existed", name);
        }
        FileProjectEntity project = FileProjectEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .createdAt(System.currentTimeMillis())
                .build();
        fileProjectRepository.insertFileProjectSelective(project);
        return fileProjectRepository.queryByNameAndNotDeleted(project.getName())
                .orElseThrow(() -> new SystemException("global.add.failed"));
    }

    /** 厂商 {@code updateFileProject:57-67}。 */
    @Override
    public FileProjectEntity updateFileProject(String projectId, String name) {
        // 照抄：这里用 selectByPrimaryKey（**不过滤 deleted**）
        FileProjectEntity project = fileProjectRepository.queryById(projectId)
                .orElseThrow(() -> new WrongArgumentException("software.business.software.id.not.found"));
        boolean existName = fileProjectRepository.queryByNameAndNotDeletedExceptId(name, projectId)
                .isPresent();
        if (existName) {
            throw new WrongArgumentException("software.name.existed", name);
        }
        project.setName(name);
        project.setUpdatedAt(System.currentTimeMillis());
        // 厂商 updateByPrimaryKey：整行 SET（created_at/deleted 原样写回）
        fileProjectRepository.updateFileProject(project);
        return fileProjectRepository.queryByNameAndNotDeleted(project.getName())
                .orElseThrow(() -> new SystemException("auth.updatea.auth.info.failed"));
    }

    /** 厂商 {@code removeFileProject:69-75}。 */
    @Override
    public void removeFileProject(String projectId) {
        FileProjectEntity project = fileProjectRepository.queryByIdAndNotDeleted(projectId)
                .orElseThrow(() -> new WrongArgumentException("software.delete.failed.not.exist"));
        // 照抄：物理删除，不是把 deleted 置 true
        fileProjectRepository.deleteFileProjectById(project.getId());
    }
}
