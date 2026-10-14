package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `Identifier`（`SoftwareService.getSoftwareCode:3171-3184`）：软件申请码的载荷。
 * <p>**字段顺序就是契约**：`serviceIdList` 在前、`machineCode` 在后。
 * JSON 的组装放在 trigger 层（用显式 ObjectNode，避免依赖注解顺序），见 `SoftwareController.softwareCode`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareIdentifierVO {

    private List<String> serviceIdList;
    private String machineCode;
}
