package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 容器环境变量（厂商 `io.kubernetes.client.openapi.models.V1EnvVar` 的 `name`/`value` 两个字段）。
 * <p>用于作战模式切换：厂商 `ModeServiceImpl.startModeType:118-137` 就是在这个粒度上
 * "删掉旧 MODE 项 → 设值 → 追加回列表"。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnvVarVO {

    private String name;
    private String value;
}
