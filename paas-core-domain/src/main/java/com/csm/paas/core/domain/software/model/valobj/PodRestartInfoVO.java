package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 某服务的 Pod 重启记录（厂商 `PodRestartInfo`：`{podName, restartCount, createTime}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PodRestartInfoVO {

    private String podName;
    private Integer restartCount;
    private Long createTime;
}
