package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 服务发现配置的修改请求（厂商 `UpdateServiceDiscoveryForm`；trigger 把 DTO 映射成本 VO）。
 *
 * <p>`type` 决定两条路径（`docs/findings/118`）：`"Service"` ⇒ 改 K8s Service 的端口与类型；
 * 其它 ⇒ 写 `g_service_port` + 改工作负载容器端口。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceDiscoveryVO {

    private String type;
    private String serviceName;
    private List<UpdatePortItemVO> portList;
}
