package com.csm.paas.core.domain.software.adapter.port;

import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;

import java.util.List;

/**
 * 运行态服务的只读入口（远程/集群读取）。
 * <p>实现必须放在 {@code infrastructure/adapter/port}，并且只能调用 {@code gateway/}。
 */
public interface IServiceRuntimePort {

    List<ServiceRuntimeEntity> queryServices(String namespace);

    /**
     * 集群里**所有节点 label** 的键与去重值（厂商 `ServiceController.getLabels:964-990`）：
     * 遍历 `listNode` 的每个 `metadata.labels`，按 label 键聚合出值集合。
     * <p><b>顺序不可依赖</b>：厂商是 `HashMap` 聚合再 `forEach`，两次调用顺序都可能不同——
     * 所以对拉时只能比集合，不能比数组顺序。
     */
    java.util.Map<String, java.util.List<String>> queryNodeLabels();
}
