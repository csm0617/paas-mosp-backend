package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.trigger.assembler.ServiceListAssembler;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 GET /v1/service 的重写版（纵向切片）。
 * <p>Trigger 只做路由：查领域服务 → 交给 {@link ServiceListAssembler} 装配。
 * <p><b>原型尚未实现认证</b>（厂商侧需要 gateway-token cookie，见工单 10）。
 */
@RestController
public class ServiceController {

    @Resource
    private IServiceQueryService serviceQueryService;

    @Resource
    private AuthSupport authSupport;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /**
     * 厂商 `GET /v1/service/labels`：集群里**所有节点 label** 的键与去重值，产出 `[{name,labels}]`。
     * <p>取节点失败时厂商抛 `SystemException("k8s.get.node.failed")`（HTTP 500）——我们交给全局异常处理器，
     * 落进厂商同款 `code=-1` 信封。
     * <p>**顺序不要依赖**：厂商用 `HashMap` 聚合再 `forEach`，同一环境两次调用顺序都可能不同。
     */
    @GetMapping("/v1/service/labels")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.LabelListDTO>> labels() {
        var merged = serviceQueryService.queryNodeLabels();
        var list = new java.util.ArrayList<com.csm.paas.core.api.dto.LabelListDTO>();
        merged.forEach((k, v) -> list.add(com.csm.paas.core.api.dto.LabelListDTO.builder().name(k).labels(v).build()));
        return ResponseDTO.ok(list);
    }

    @GetMapping("/v1/service")
    public ResponseDTO<ServiceListResponseDTO> list(HttpServletRequest request) {
        // 厂商在这两个端点**不做**应用级校验（实测无 cookie 也 200），
        // 鉴权由 gateway-auth 在边缘完成，见 docs/findings/14-vendor-auth-is-gateway-side.md

        ServiceListEntity entity = serviceQueryService.queryServiceList(namespace);
        return ResponseDTO.ok(ServiceListAssembler.toResponse(entity));
    }
}
