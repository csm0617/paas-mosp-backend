package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/service/serviceInfoByNode` 的响应体（厂商 `third/response/tenx/v2/services/list/ServiceList`：
 * `{runningDeployment, total, count, services}`）。
 *
 * <p>照抄的怪点（`ServiceController.pagination:2136-2158`）：
 * <ul>
 *   <li>`runningDeployment` **从不赋值** ⇒ 恒为 `0`（int 默认值）；</li>
 *   <li>`list.size() &lt; size` 时**直接返回整个列表**，`page` 参数被忽略（第 2 页也会拿到同一批）；</li>
 *   <li>`page &gt; totalPage` 时返回空数组，但 `total` 仍是全量条数；</li>
 *   <li>`totalPage = list.size() / size + (hasNextPage ? 1 : 0)`，其中 `hasNextPage = size % size != 0`。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>（监控补齐依赖 Prometheus）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceNodeListVO {

    private Integer runningDeployment;

    private Integer total;

    private Integer count;

    private List<ServiceNodeItemVO> services;
}
