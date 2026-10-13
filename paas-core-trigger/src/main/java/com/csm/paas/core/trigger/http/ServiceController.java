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

    @GetMapping("/v1/service")
    public ResponseDTO<ServiceListResponseDTO> list(HttpServletRequest request) {
        // 厂商在这两个端点**不做**应用级校验（实测无 cookie 也 200），
        // 鉴权由 gateway-auth 在边缘完成，见 docs/findings/14-vendor-auth-is-gateway-side.md

        ServiceListEntity entity = serviceQueryService.queryServiceList(namespace);
        return ResponseDTO.ok(ServiceListAssembler.toResponse(entity));
    }
}
