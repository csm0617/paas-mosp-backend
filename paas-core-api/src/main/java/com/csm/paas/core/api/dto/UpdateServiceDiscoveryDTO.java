package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `UpdateServiceDiscoveryForm`（`controller/form/service/UpdateServiceDiscoveryForm.java`）：
 * `PUT /v1/service/patchService` 的请求体。
 *
 * <p>`type` 决定两条完全不同的路径（见 `docs/findings/118`）：`"Service"` ⇒ K8s 只读+patch（改服务发现）；
 * 其它值 ⇒ 写 `g_service_port` + patch 工作负载容器端口。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceDiscoveryDTO {

    private String type;
    private String serviceName;
    private List<UpdatePortItemDTO> portList;
}
