package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.file.adapter.repository.IFileProjectRepository;
import com.csm.paas.core.domain.file.model.entity.FileProjectEntity;
import com.csm.paas.core.infrastructure.dao.IFileProjectDao;
import com.csm.paas.core.infrastructure.dao.po.FileProjectPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code g_file_project} 仓储实现（厂商 {@code FileProjectMapper}）。 */
@Repository
@RequiredArgsConstructor
public class FileProjectRepository implements IFileProjectRepository {

    private final IFileProjectDao fileProjectDao;

    @Override
    public List<FileProjectEntity> queryFileProjectPage(String likeKeyword, int offset, int size) {
        return fileProjectDao.selectFileProjectPage(likeKeyword, offset, size).stream()
                .map(FileProjectRepository::toEntity)
                .toList();
    }

    @Override
    public long countFileProjects(String likeKeyword) {
        return fileProjectDao.countFileProjects(likeKeyword);
    }

    @Override
    public Optional<FileProjectEntity> queryByIdAndNotDeleted(String id) {
        return Optional.ofNullable(fileProjectDao.selectByIdAndNotDeleted(id))
                .map(FileProjectRepository::toEntity);
    }

    @Override
    public Optional<FileProjectEntity> queryById(String id) {
        return Optional.ofNullable(fileProjectDao.selectById(id)).map(FileProjectRepository::toEntity);
    }

    @Override
    public Optional<FileProjectEntity> queryByNameAndNotDeleted(String name) {
        return Optional.ofNullable(fileProjectDao.selectByNameAndNotDeleted(name))
                .map(FileProjectRepository::toEntity);
    }

    @Override
    public Optional<FileProjectEntity> queryByNameAndNotDeletedExceptId(String name, String excludeId) {
        return Optional.ofNullable(fileProjectDao.selectByNameAndNotDeletedExceptId(name, excludeId))
                .map(FileProjectRepository::toEntity);
    }

    @Override
    public int insertFileProjectSelective(FileProjectEntity project) {
        return fileProjectDao.insertFileProjectSelective(FileProjectPO.builder()
                .id(project.getId())
                .name(project.getName())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .deleted(project.getDeleted())
                .build());
    }

    @Override
    public int updateFileProject(FileProjectEntity project) {
        return fileProjectDao.updateFileProject(FileProjectPO.builder()
                .id(project.getId())
                .name(project.getName())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .deleted(project.getDeleted())
                .build());
    }

    @Override
    public int deleteFileProjectById(String id) {
        return fileProjectDao.deleteFileProjectById(id);
    }

    private static FileProjectEntity toEntity(FileProjectPO po) {
        return FileProjectEntity.builder()
                .id(po.getId())
                .name(po.getName())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .deleted(po.getDeleted())
                .build();
    }
}
