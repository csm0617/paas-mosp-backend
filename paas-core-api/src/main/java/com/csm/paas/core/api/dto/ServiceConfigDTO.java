package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `ServiceConfig`：`{nodePorts, type}`（`type` 默认 `ClusterIP`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceConfigDTO {

    private List<NodePortConfigDTO> nodePorts;
    private String type;
}
