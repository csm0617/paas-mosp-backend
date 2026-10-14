package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.whitelist.adapter.repository.IWhiteListRepository;
import com.csm.paas.core.domain.whitelist.model.entity.WhiteListEntity;
import com.csm.paas.core.infrastructure.dao.IWhiteListDao;
import com.csm.paas.core.infrastructure.dao.po.WhiteListPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 白名单仓储实现（只调 dao）。 */
@Repository
@RequiredArgsConstructor
public class WhiteListRepository implements IWhiteListRepository {

    private final IWhiteListDao whiteListDao;

    @Override
    public List<WhiteListEntity> queryPage(String likeKeyword, int offset, int size) {
        return whiteListDao.selectPage(likeKeyword, offset, size).stream().map(this::toEntity).toList();
    }

    @Override
    public long count(String likeKeyword) {
        return whiteListDao.selectCount(likeKeyword);
    }

    @Override
    public Optional<WhiteListEntity> queryByPath(String path) {
        return Optional.ofNullable(whiteListDao.selectByPath(path)).map(this::toEntity);
    }

    @Override
    public Optional<WhiteListEntity> queryById(long id) {
        return Optional.ofNullable(whiteListDao.selectById(id)).map(this::toEntity);
    }

    @Override
    public void insert(String path, long createdAt) {
        whiteListDao.insert(path, createdAt);
    }

    @Override
    public void deleteById(long id) {
        whiteListDao.deleteById(id);
    }

    private WhiteListEntity toEntity(WhiteListPO po) {
        return WhiteListEntity.builder()
                .id(po.getId()).path(po.getPath())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt())
                .build();
    }
}
