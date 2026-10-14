package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `SoftwareAuth`（2 键）：批量操作的结果项。`result` 在取消授权路径上恒为 **false**。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareAuthDTO {

    private String softwareKey;
    private boolean result;
}
