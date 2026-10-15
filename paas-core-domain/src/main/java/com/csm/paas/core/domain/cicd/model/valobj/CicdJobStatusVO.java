package com.csm.paas.core.domain.cicd.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个 Job 的四个状态读数（厂商 `CicdService.cicdList:224-250` / `continuousIntegrationsV2:494-510`
 * 读的 `V1JobStatus.getActive()/getSucceeded()/getFailed()/getReady()`）。
 *
 * <p>四个都是 **Integer（可 null）**：厂商用 `!= null` 判断"这个阶段有没有出现过"，
 * 而不是判 0/非 0——所以 VO 必须保留 null 语义（`0` 与 `null` 结论不同）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdJobStatusVO {

    private Integer active;
    private Integer succeeded;
    private Integer failed;
    private Integer ready;
}
