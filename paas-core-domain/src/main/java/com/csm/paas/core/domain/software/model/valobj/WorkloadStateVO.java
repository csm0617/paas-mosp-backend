package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作负载的"副本与状态"读数（`batch-start` / `batch-stop` 需要的那几个字段）。
 *
 * <p>为什么要一个 VO：厂商为 Deployment 与 StatefulSet 各写了一份状态推导
 * （`getServiceStatusByServicesItem` 的两个重载），字段集一样但判断条件**不同**（见 `findings/126`），
 * 所以领域层要同时知道"是哪种工作负载"和这几个数字。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkloadStateVO {

    /** true = Deployment；false = StatefulSet。 */
    private boolean deployment;

    /** `spec.replicas`（`batch-stop` 用它判"已经停了"）。 */
    private Integer specReplicas;

    /** 以下五个都来自 `status`。 */
    private Integer replicas;
    private Integer readyReplicas;
    private Integer availableReplicas;
    private Integer updatedReplicas;
    private Integer unavailableReplicas;
}
