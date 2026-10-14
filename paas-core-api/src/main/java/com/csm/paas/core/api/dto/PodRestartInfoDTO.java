package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `PodRestartInfo`（3 键）：某服务的 Pod 重启记录。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PodRestartInfoDTO {

    private String podName;
    private Long createTime;
    private Integer restartCount;
}
