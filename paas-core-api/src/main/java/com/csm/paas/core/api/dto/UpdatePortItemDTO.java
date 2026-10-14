package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `UpdatePortItem`（`UpdateServiceDiscoveryForm.portList` 的元素）。
 * <p>**三个端口字段别混**：`containerPort`（容器端口，B 分支写进工作负载用）、
 * `port`（映射端口，B 分支写进 `g_service_port` 用）、`targetPort`（目标端口，两条分支都没用到）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePortItemDTO {

    private Integer containerPort;
    private String protocol;
    private String name;
    private Integer port;
    private Integer targetPort;
}
