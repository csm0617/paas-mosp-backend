package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.NodePortUsedInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `PublicController`（`/v1/pub`）——目前只实现 **`GET /nodePortUsed`**。
 *
 * <p>厂商这个控制器里有大量**节点/集群运维**端点（增删节点、监控、重启/关机、fdfs、etcd、vip 等），
 * 其中不少是**破坏性**或依赖 SSH/监控组件；未实现清单与分类见 `docs/findings/141`。
 */
@RestController
@RequestMapping("/v1/pub")
@RequiredArgsConstructor
public class PublicController {

    private final IServiceQueryService serviceQueryService;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /**
     * 厂商 `GET /v1/pub/nodePortUsed?port=`：已被占用的 NodePort 列表（`port` 非空时只返回该端口）。
     *
     * <p>三段来源：ingress-lb Deployment 的 httpPort/httpsPort 注解、tcp/udp-services ConfigMap、
     * `type=NodePort` 的 Service。规则见 `IServiceQueryService.queryNodePortUsed`（规格 `findings/141`）。
     */
    @GetMapping("/nodePortUsed")
    public ResponseDTO<java.util.List<NodePortUsedInfoDTO>> nodePortUsed(
            @RequestParam(required = false) Integer port) {
        return ResponseDTO.ok(serviceQueryService.queryNodePortUsed(namespace, port).stream()
                .map(v -> NodePortUsedInfoDTO.builder().name(v.getName()).port(v.getPort()).build())
                .toList());
    }
}
