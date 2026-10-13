package com.csm.paas.core.domain.mode.service;

import com.csm.paas.core.domain.mode.model.entity.ModeEntity;

import java.util.List;
import java.util.Optional;

/** 作战模式查询（厂商 GET /v1/mode/current 与 /v1/mode/list）。 */
public interface IModeQueryService {

    Optional<ModeEntity> queryCurrent();

    List<ModeEntity> queryAll();
}
