package com.csm.paas.core.domain.mode.adapter.repository;

import com.csm.paas.core.domain.mode.model.entity.ModeEntity;

import java.util.List;
import java.util.Optional;

public interface IModeRepository {

    Optional<ModeEntity> queryCurrent();

    List<ModeEntity> queryAll();

    /** 按主键查（厂商 `ModeServiceImpl.startModeType:117` 的 `modeMapper.selectByPrimaryKey(id)`）。 */
    Optional<ModeEntity> queryById(Long id);

    /**
     * 厂商 `changeModeTypeDB:147` 第一步：**全局**把 `used=1` 清 0，返回受影响行数。
     */
    int resetUsed();

    /**
     * 厂商 `changeModeTypeDB:148` 第二步：把该 id 置 `used=1`，返回受影响行数
     * （`startModeType` 忽略这个返回值，恒返回 1）。
     */
    int setUsedById(Long id);
}
