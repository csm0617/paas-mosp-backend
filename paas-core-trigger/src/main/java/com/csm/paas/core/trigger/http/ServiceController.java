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

    /** 厂商同名配置：Harbor 地址（用于拼 initContainer 镜像前缀）。 */
    @Value("${server.host.harbor:}")
    private String harborHost;

    /** 厂商同名配置：`initPodConfig.image`（默认值与厂商一致）。 */
    @Value("${initPodConfig.image:curl-alpine:latest}")
    private String initPodImage;

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

    /**
     * 厂商 `GET /v1/service/serviceAuth`：授权视图的服务列表。
     * <p>三段过滤见 `IServiceQueryService.queryServiceAuthList`；`keyword > 45` → 400。
     * <p><b>注意</b>：本环境 `system/appName` 标签缺失 ⇒ 基础过滤全保留 ⇒ 不传 `authType` 时应为 9 条。
     */
    @GetMapping("/v1/service/serviceAuth")
    public ResponseDTO<com.csm.paas.core.api.dto.ServiceListResponseDTO> serviceAuthList(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "1") Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "20") Integer size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String authType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String serviceType) {
        if (keyword != null && keyword.length() > 45) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.invalid.service.name.length");
        }
        return ResponseDTO.ok(ServiceListAssembler.toResponse(serviceQueryService.queryServiceAuthList(
                namespace, PageUtils.normalizePage(page), PageUtils.normalizeSize(size),
                keyword, authType, serviceType)));
    }

    /**
     * 厂商 `PUT /v1/service/hostName`：改服务 hostname（先 Deployment，读不到回退 StatefulSet）。
     * <p>**首位 no-op**：请求 hostName 与现有 hostname 都空白时直接 200 true、不做任何修改。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/hostName")
    public ResponseDTO<Boolean> updateHostName(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.HostNameDTO form) {
        return ResponseDTO.ok(serviceQueryService.updateServiceHostname(
                namespace, form.getServiceName(), form.getHostName()));
    }

    /**
     * 厂商 `POST /v1/service/priority?serviceName&level`：改服务优先级（`priorityClassName`）。
     * <p>两个参数都是 **query parameter**；`level` 空白 → 设为空串；服务不存在 → 400。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/priority")
    public ResponseDTO<Boolean> updatePriority(
            @org.springframework.web.bind.annotation.RequestParam String serviceName,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String level) {
        return ResponseDTO.ok(serviceQueryService.updateServicePriority(namespace, serviceName, level));
    }

    /**
     * 厂商 `PUT /v1/service/scale?serviceName&scale`：改副本数，返回**整份 patch 后的对象**。
     * <p>先 strategic-merge-patch Deployment；失败回退 StatefulSet；都失败 → 500/`code=-1`。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/scale")
    public ResponseDTO<java.util.Map<String, Object>> scale(
            @org.springframework.web.bind.annotation.RequestParam String serviceName,
            @org.springframework.web.bind.annotation.RequestParam int scale) {
        return ResponseDTO.ok(serviceQueryService.scaleService(namespace, serviceName, scale));
    }

    /**
     * 厂商 `GET /v1/service/otherPlugin`：跨命名空间的"其它插件"工作负载（三类原始 K8s 对象列表）。
     * <p>排除 `gaop-namespace` 并套厂商的标签选择器；某一类失败只返回空列表（照抄）。
     */
    @GetMapping("/v1/service/otherPlugin")
    public ResponseDTO<java.util.Map<String, java.util.List<java.util.Map<String, Object>>>> otherPlugin() {
        return ResponseDTO.ok(serviceQueryService.queryOtherPluginWorkloads());
    }

    /**
     * 厂商 `PUT /v1/service/patchCommand/{serviceName}`：改服务启动命令（`{command,args}` 作用到**所有容器**）。
     * <p>服务不存在 → 400 `service.service.name.not.found`；厂商返回**不带 body** ⇒ 这里也回 `data:null`。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/patchCommand/{serviceName}")
    public ResponseDTO<Void> updateCommand(
            @org.springframework.web.bind.annotation.PathVariable String serviceName,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.UpdateCommandDTO form) {
        serviceQueryService.updateServiceCommand(namespace, serviceName, form.getCommand(), form.getArgs());
        return ResponseDTO.ok(null);
    }

    /**
     * 厂商 `GET /v1/service/restartInfo?serviceName=`：某服务的 Pod 重启记录（`{podName,createTime,restartCount}`）。
     * <p>查 `g_pod_event`（`service_name` 等值、`create_at` 倒序）；**没有记录返回空数组**。
     */
    @GetMapping("/v1/service/restartInfo")
    public ResponseDTO<java.util.List<com.csm.paas.core.api.dto.PodRestartInfoDTO>> restartInfo(
            @org.springframework.web.bind.annotation.RequestParam String serviceName) {
        return ResponseDTO.ok(serviceQueryService.queryRestartInfo(serviceName).stream()
                .map(v -> com.csm.paas.core.api.dto.PodRestartInfoDTO.builder()
                        .podName(v.getPodName()).createTime(v.getCreateTime()).restartCount(v.getRestartCount())
                        .build())
                .toList());
    }

    /**
     * 厂商 `POST /v1/service/cancelStartUp`：取消服务的延时启动（请求体 `{list}`）。
     * <p>逐个服务删注解 `gaop/serviceInitTime`、清 `initContainers` 并整份 replace；
     * 任一服务读不到 → 400 `service.get.service.config.exception.cancel.delay`。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/cancelStartUp")
    public ResponseDTO<Boolean> cancelStartUp(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.StartUpServiceListDTO form) {
        return ResponseDTO.ok(serviceQueryService.cancelServiceStartup(namespace, form.getList()));
    }

    /**
     * 厂商 `POST /v1/service/startUp`：设置/取消服务的延时启动（请求体 `{list, time}`）。
     * <p>`time<0` → 400；`time=0` 取消（删注解+清 initContainers+整份 replace）；`time>0` 写注解并注入
     * `startup` initContainer（镜像来自 `server.host.harbor` + `initPodConfig.image`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/startUp")
    public ResponseDTO<Boolean> startUp(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.InitServiceInfoDTO form) {
        return ResponseDTO.ok(serviceQueryService.setServiceStartup(
                namespace, form.getList(), form.getTime() == null ? 0L : form.getTime(),
                harborHost, initPodImage));
    }

    /**
     * 厂商 `POST /v1/service/{serviceName}/node_port`：设置 NodePort。
     * <p>返回**布尔**（厂商 `Response.ok(v1Service != null)`）；规则与两个分支见
     * `IServiceQueryService.updateServiceNodePort`。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/{serviceName}/node_port")
    public ResponseDTO<Boolean> updateNodePort(
            @org.springframework.web.bind.annotation.PathVariable String serviceName,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ServiceConfigDTO config) {
        if (serviceName == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.name.not.null");
        }
        if (config == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.config.not.null");
        }
        var nodePorts = config.getNodePorts() == null ? java.util.List.<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO>of()
                : config.getNodePorts().stream()
                .map(p -> com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO.builder()
                        .port(p.getPort()).protocol(p.getProtocol()).nodePort(p.getNodePort()).build())
                .toList();
        return ResponseDTO.ok(serviceQueryService.updateServiceNodePort(
                namespace, serviceName, config.getType(), nodePorts));
    }

    /**
     * 厂商 `POST /v1/service/{serviceName}/nodePort`：设置 NodePort，并管理 `<name>-nodeport` Service。
     * <p>规则与四处怪点见 `IServiceQueryService.updateServiceNodePortsEndpoint`（`findings/88`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/{serviceName}/nodePort")
    public ResponseDTO<Boolean> updateNodePorts(
            @org.springframework.web.bind.annotation.PathVariable String serviceName,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ServiceConfigDTO config) {
        if (serviceName == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.name.not.null");
        }
        if (config == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.config.not.null");
        }
        var nodePorts = config.getNodePorts() == null ? java.util.List.<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO>of()
                : config.getNodePorts().stream()
                .map(p -> com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO.builder()
                        .port(p.getPort()).protocol(p.getProtocol()).nodePort(p.getNodePort()).build())
                .toList();
        return ResponseDTO.ok(serviceQueryService.updateServiceNodePortsEndpoint(
                namespace, serviceName, config.getType(), nodePorts));
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
