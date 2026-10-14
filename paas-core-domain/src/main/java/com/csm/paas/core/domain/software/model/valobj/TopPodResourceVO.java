package com.csm.paas.core.domain.software.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * Prometheus 单点查询的一条结果（厂商 `TopPodResource{podName, value, namespace}`，
 * 由 `SoftwareService.getResourceMetric:2023-2047` 从 `data.result[]` 解析）。
 */
@Data
@Builder
public class TopPodResourceVO {

    /** `metric.pod_name`。 */
    private String podName;
    /** `value[1]` 的**字符串原样**（照抄厂商 `values.get(1).toString()`）。 */
    private String value;
    /** `metric.pod_namespace`。 */
    private String namespace;
}
