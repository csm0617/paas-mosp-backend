package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 软件总览的三个计数（厂商 `POST /v1/software/overView` → `OverviewInfo`）。
 * <p>每个分组都是 `StatusInfo{running, stop, pending}`，三处都是**从 0 开始累加**。
 * <p>规格见 `docs/findings/129`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareOverviewEntity {

    private StatusInfo software;
    private StatusInfo service;
    private StatusInfo pod;

    /** 厂商 `StatusInfo`（三个字段默认 0）。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatusInfo {
        private Integer running;
        private Integer stop;
        private Integer pending;
    }
}
