package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `OverviewInfo`（`POST /v1/software/overView` 的响应）：三组 `StatusInfo{running, stop, pending}`。
 * <p>注意**三组的统计口径不同**（见 `docs/findings/129`）：software 按"关联服务状态汇总"、
 * service 按标签过滤后的工作负载状态、pod 按 pod phase。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OverviewInfoDTO {

    private StatusInfoDTO software;
    private StatusInfoDTO service;
    private StatusInfoDTO pod;

    /** 厂商 `StatusInfo`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatusInfoDTO {
        private Integer running;
        private Integer stop;
        private Integer pending;
    }
}
