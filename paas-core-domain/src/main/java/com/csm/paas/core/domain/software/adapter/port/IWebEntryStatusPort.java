package com.csm.paas.core.domain.software.adapter.port;

import java.util.List;
import java.util.Map;

/**
 * web 入口健康态的查询端口（实现在 infrastructure，读 K8s Deployment）。
 * <p>厂商出处：`SoftwareService.getSoftwareList:515-527` 与 `setServiceStatusBySoftware:564-585`——
 * 先按 `g_service_port` 的 `service_name` 去 Deployment 列表里找同名的，再按 Deployment 的
 * `status` 算出一个 0/1/2/3 的状态码。
 */
public interface IWebEntryStatusPort {

    /**
     * @param serviceNames Deployment 名列表
     * @return 找到的 Deployment 名 → 状态码（1/2/3）；**找不到的名字不在返回里**（调用方按 2 处理）
     */
    Map<String, Integer> queryWebStatus(List<String> serviceNames);
}
