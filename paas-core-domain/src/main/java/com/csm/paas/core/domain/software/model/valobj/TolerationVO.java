package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个污点容忍（厂商 `V1Toleration` 的**三个字段**——它只用了这三个）。
 * <p>`POST /v1/service/tolerance` 用的那条是固定的：
 * `{key: "node-role.kubernetes.io/master", operator: "Exists", effect: "NoSchedule"}`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TolerationVO {

    private String key;
    private String operator;
    private String effect;
}
