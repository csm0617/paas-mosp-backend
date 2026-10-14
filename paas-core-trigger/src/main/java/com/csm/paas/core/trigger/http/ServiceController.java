package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.trigger.assembler.ServiceListAssembler;
import com.csm.paas.core.types.util.PageUtils;
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
     * 厂商 `PUT /v1/service/updateName?serviceKey&name&nameUs&nameFr`：改服务显示名。
     * <p>四个参数都是 **query parameter**（不是 body）。规则与未验证说明见 `IServiceQueryService.updateServiceName`。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/updateName")
    public ResponseDTO<Boolean> updateServiceName(
            @org.springframework.web.bind.annotation.RequestParam String serviceKey,
            @org.springframework.web.bind.annotation.RequestParam String name,
            @org.springframework.web.bind.annotation.RequestParam String nameUs,
            @org.springframework.web.bind.annotation.RequestParam String nameFr) {
        return ResponseDTO.ok(serviceQueryService.updateServiceName(namespace, serviceKey, name, nameUs, nameFr));
    }

    /**
     * 厂商 `GET /v1/service/labels`：集群里**所有节点 label** 的键与去重值，产出 `[{name,labels}]`。
     * <p>取节点失败时厂商抛 `SystemException("k8s.get.node.failed")`（HTTP 500）——我们交给全局异常处理器，
     * 落进厂商同款 `code=-1` 信封。
     * <p>**顺序不要依赖**：厂商用 `HashMap` 聚合再 `forEach`，同一环境两次调用顺序都可能不同。
     */
    /**
     * 厂商 `GET /v1/service/gaop?page&size&type&keyword`：框架系统服务（`type=1`）/ 基础服务（`type=2`）列表。
     * <p>`type` **必填**；`keyword` 长度 > 45 → 400 `service.invalid.service.name.length`。
     * <p>响应形状与 `GET /v1/service` 相同（`{runningDeployment,total,count,services}`）。
     * <p><b>注意</b>：厂商在没有 `system/appType` 标签时，`type=2` 走的也是框架名单（照抄的缺陷，
     * 见 `GaopServiceFilter` 与 `docs/findings/53` §5）。
     */
    @GetMapping("/v1/service/gaop")
    public ResponseDTO<com.csm.paas.core.api.dto.ServiceListResponseDTO> gaopServiceList(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "1") Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "20") Integer size,
            @org.springframework.web.bind.annotation.RequestParam Integer type,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword) {
        if (keyword != null && keyword.length() > 45) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.invalid.service.name.length");
        }
        var entity = serviceQueryService.queryGaopServiceList(
                namespace, PageUtils.normalizePage(page), PageUtils.normalizeSize(size), type, keyword);
        return ResponseDTO.ok(ServiceListAssembler.toResponse(entity));
    }

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
