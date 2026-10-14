package com.csm.paas.core.domain.node.service;

import java.util.List;

/**
 * 公共 API - 服务标识列表（厂商 `controller/pub/PublicController.serviceKeyInfo:2029-2068`）。
 *
 * <p>放在 {@code domain/node} 下是**因为本次改动的文件白名单**只放行
 * `domain/node/**`（见任务约束第 3 条），并不是说它属于节点域——它读的是
 * `gaop-namespace` 下的 Deployment。
 */
public interface IPubServiceQueryService {

    /**
     * 厂商 `PublicController.serviceKeyInfo:2032-2067` 的逐字语义：
     * <ol>
     *   <li>列 `gaop-namespace` 全部 Deployment；失败抛 `service.get.service.name.list.error`；</li>
     *   <li>跳过没有 {@code system/appName} 标签的、{@code system/appName == "gaop"} 的、
     *       以及**名字等于入参 {@code name}** 的；</li>
     *   <li>{@code serviceName} 非空白时做 {@code replaceAll(" +", "")} 后当**正则** {@code find()} 过滤
     *       （厂商 `:2035`、`:2057-2061`，是 `Pattern.compile(keyword)` 不是字面量匹配）。</li>
     * </ol>
     *
     * @param name        当前查看的服务名（结果里排除它自身）
     * @param serviceName 关键字（正则）
     */
    List<String> serviceKeyList(String name, String serviceName);
}
