package com.csm.paas.core.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 厂商 `DELETE /v1/service/servicePods` 的请求体（厂商 `DeletePodsForm`）。
 *
 * <p>厂商出处：`ServiceController.java:2327-2343`（`deleteServicePods`）。
 * **破坏性操作**：直接删 Pod 容器，照抄，不做额外保护。
 */
@Data
public class DeletePodsDTO {

    private List<String> podNames;
    /** 删除的命名空间（厂商直接拿请求体里的值，**不校验**）。 */
    private String namespace;
    /** true ⇒ `gracePeriodSeconds = 0`（强删）；否则该字段留 null（走 API Server 默认宽限期）。 */
    private Boolean force;
}
