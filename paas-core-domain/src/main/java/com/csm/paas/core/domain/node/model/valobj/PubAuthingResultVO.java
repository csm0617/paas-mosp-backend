package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条服务的授权结果（厂商 `controller/response/AuthingResult{serviceId,result}`）。
 *
 * <p>照抄点：`result` 是 **boolean 且默认 false**（`AuthingResult.java:8`）——
 * 只有当 upsert 恰好影响 1 行时才置 true；影响 0 行（或抛异常前）保持 false。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubAuthingResultVO {

    private String serviceId;

    @Builder.Default
    private boolean result = false;
}
