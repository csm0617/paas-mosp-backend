package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.file.adapter.repository.IFileRepository;
import com.csm.paas.core.domain.file.model.entity.FileEntity;
import com.csm.paas.core.infrastructure.dao.IFileDao;
import com.csm.paas.core.infrastructure.dao.po.FilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code g_file} 仓储实现（厂商 {@code FileMapper}）。 */
@Repository
@RequiredArgsConstructor
public class FileRepository implements IFileRepository {

    private final IFileDao fileDao;

    @Override
    public List<FileEntity> queryFilePage(String projectId, Long start, Long end, String likeKeyword,
                                          int startNumber, int limitNumber) {
        return fileDao.selectFilePage(start, end, likeKeyword, projectId, startNumber, limitNumber)
                .stream()
                .map(FileRepository::toEntity)
                .toList();
    }

    @Override
    public long countFilesByProjectId(String projectId) {
        Long count = fileDao.countFileSize(projectId);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<FileEntity> queryById(String id) {
        return Optional.ofNullable(fileDao.selectById(id)).map(FileRepository::toEntity);
    }

    @Override
    public Optional<FileEntity> queryByIdAndNotDeleted(String id) {
        return Optional.ofNullable(fileDao.selectByIdAndNotDeleted(id)).map(FileRepository::toEntity);
    }

    @Override
    public Optional<FileEntity> queryByPath(String path) {
        return Optional.ofNullable(fileDao.selectByPath(path)).map(FileRepository::toEntity);
    }

    @Override
    public int insertFileSelective(FileEntity file) {
        return fileDao.insertFileSelective(FilePO.builder()
                .id(file.getId())
                .name(file.getName())
                .fileProjectId(file.getFileProjectId())
                .path(file.getPath())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .deleted(file.getDeleted())
                .currentSize(file.getCurrentSize())
                .size(file.getSize())
                .md5(file.getMd5())
                .build());
    }

    @Override
    public int deleteById(String id) {
        return fileDao.deleteById(id);
    }

    private static FileEntity toEntity(FilePO po) {
        return FileEntity.builder()
                .id(po.getId())
                .name(po.getName())
                .fileProjectId(po.getFileProjectId())
                .path(po.getPath())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .deleted(po.getDeleted())
                .currentSize(po.getCurrentSize())
                .size(po.getSize())
                .md5(po.getMd5())
                .build();
    }
}
