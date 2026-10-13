package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;

/** 软件-服务-端口 域的查询服务。 */
public interface IServiceQueryService {

    ServiceListEntity queryServiceList(String namespace);

    /**
     * 节点 label 的键与去重值（厂商 `GET /v1/service/labels`）。
     * <p>返回 `label键 → 该键的去重值列表`；**顺序不可依赖**（厂商用 HashMap 聚合）。
     */
    java.util.Map<String, java.util.List<String>> queryNodeLabels();
}
