package com.csm.paas.core.domain.mode.adapter.repository;

import com.csm.paas.core.domain.mode.model.entity.ModeEntity;

import java.util.List;
import java.util.Optional;

public interface IModeRepository {

    Optional<ModeEntity> queryCurrent();

    List<ModeEntity> queryAll();
}
