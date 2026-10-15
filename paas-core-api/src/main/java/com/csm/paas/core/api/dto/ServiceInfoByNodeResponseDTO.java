package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/service/serviceInfoByNode` 的响应体（厂商 `third/response/tenx/v2/services/list/ServiceList`：
 * `{runningDeployment, total, count, services}`）。
 *
 * <p>厂商出处：`ServiceController.getServiceListByNode:1691-1728` + `pagination:2136-2158`。
 * <p>列表项复用 {@link ServiceDetailDTO}（厂商 `ServicesItem` 的 20 键；`cpuOrder`/`memoryOrder`/`gpuOrder`
 * 是 **Double**，正是本端点排序用的那几个字段）。
 *
 * <p>照抄的怪点：`runningDeployment` 厂商**从不赋值** ⇒ 恒为 `0`。
 *
 * <p><b>类 C：本环境不可验证</b>——每服务的监控补齐依赖 Prometheus 的 24 小时范围查询。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceInfoByNodeResponseDTO {

    private Integer runningDeployment;

    private Integer total;

    private Integer count;

    private List<ServiceDetailDTO> services;
}
