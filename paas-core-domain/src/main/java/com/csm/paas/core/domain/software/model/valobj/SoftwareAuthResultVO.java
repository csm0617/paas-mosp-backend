package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 批量授权/取消授权的结果项（厂商 `SoftwareAuth`：`{softwareKey, result}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareAuthResultVO {

    private String softwareKey;
    private boolean result;
}
