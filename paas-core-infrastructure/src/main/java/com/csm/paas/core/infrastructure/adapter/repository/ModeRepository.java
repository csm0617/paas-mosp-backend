package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.mode.adapter.repository.IModeRepository;
import com.csm.paas.core.domain.mode.model.entity.ModeEntity;
import com.csm.paas.core.infrastructure.dao.IModeDao;
import com.csm.paas.core.infrastructure.dao.po.ModePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ModeRepository implements IModeRepository {

    private final IModeDao modeDao;

    @Override
    public Optional<ModeEntity> queryCurrent() {
        return Optional.ofNullable(modeDao.selectUsed()).map(this::toEntity);
    }

    @Override
    public List<ModeEntity> queryAll() {
        return modeDao.selectAll().stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<ModeEntity> queryById(Long id) {
        return Optional.ofNullable(modeDao.selectById(id)).map(this::toEntity);
    }

    @Override
    public int resetUsed() {
        return modeDao.resetUsed();
    }

    @Override
    public int setUsedById(Long id) {
        return modeDao.setUsedById(id);
    }

    private ModeEntity toEntity(ModePO po) {
        return ModeEntity.builder()
                .id(po.getId()).type(po.getType()).mode(po.getMode()).modeName(po.getModeName())
                .used(po.getUsed()).createTime(po.getCreateTime() == null ? 0L : po.getCreateTime())
                .build();
    }
}
