package com.csm.paas.core.domain.software.adapter.repository;

import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;

import java.util.List;
import java.util.Optional;

/**
 * 本地仓储接口（MySQL）。
 * <p>实现必须放 {@code infrastructure/adapter/repository}，且只能调 {@code dao/}。
 */
public interface IServiceAuthRepository {

    /** 全量读授权状态，按 serviceId 建索引由调用方决定。 */
    List<ServiceAuthEntity> queryAll();

    /** 按 serviceId 取（厂商 `queryServiceList`：`where service_id = ?` 取第 0 条）。 */
    Optional<ServiceAuthEntity> queryByServiceId(String serviceId);

    /**
     * 把授权状态写成"已过期"（厂商在 `softwareRelationServiceList` 里发现 `now > authTime` 时
     * 顺手改库——**读接口带副作用**，照抄）。
     *
     * @param auth 目标状态（`AuthServiceType.EXPIRED = 2`）
     */
    void updateAuth(String serviceId, Integer auth);
}
