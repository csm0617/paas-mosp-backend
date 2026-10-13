package com.csm.paas.core.domain.software.adapter.repository;

import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;

import java.util.List;

/**
 * 本地仓储接口（MySQL）。
 * <p>实现必须放 {@code infrastructure/adapter/repository}，且只能调 {@code dao/}。
 */
public interface IServiceAuthRepository {

    /** 全量读授权状态，按 serviceId 建索引由调用方决定。 */
    List<ServiceAuthEntity> queryAll();
}
