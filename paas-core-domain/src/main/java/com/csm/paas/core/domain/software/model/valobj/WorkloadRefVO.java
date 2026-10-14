package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作负载的"身份"：是 Deployment 还是 StatefulSet、以及它的 `metadata.name`。
 *
 * <p>为什么要单独一个 VO：`PUT /v1/service/patchService` 的 B 分支要拿 `metadata.name` 去查
 * `g_software_app.app_name`，而现有的读端口方法只返回端口（`findings/118` §3.1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkloadRefVO {

    /** true = Deployment；false = StatefulSet。 */
    private boolean deployment;

    /** `metadata.name`（**不一定等于请求里的 serviceName**，照抄厂商用这个值查库）。 */
    private String name;
}
