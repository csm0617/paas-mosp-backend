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

    /** 破坏性运维动作（节点调度 / 重启基础组件 / 删 Pod）。 */
    @Resource
    private com.csm.paas.core.domain.software.service.IServiceOpsService serviceOpsService;

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

    /** 厂商 `mount.path`（`application.yml:203-204`，默认 `/data/paas/`）：`df -h` 的目标挂载点。 */
    @Value("${mount.path:/data/paas/}")
    private String mountPath;

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
     * 厂商 `PUT /v1/service/patchService`：修改服务发现的配置信息（请求体 `UpdateServiceDiscoveryForm`）。
     *
     * <p>按 `type` 分两条路径：`"Service"` ⇒ 用容器端口重建 Service 的 ports 并把 type 写死 `ClusterIP`
     * （已实现）；其它 ⇒ 写 `g_service_port` + patch 工作负载容器端口（**尚未实现**，规格见 `findings/118`）。
     * <p>返回 `data:true`；错误语义见 `IServiceQueryService.updateServiceDiscovery`。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/patchService")
    public ResponseDTO<Boolean> patchService(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.UpdateServiceDiscoveryDTO form) {
        var vo = com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO.builder()
                .type(form == null ? null : form.getType())
                .serviceName(form == null ? null : form.getServiceName())
                .portList(form == null || form.getPortList() == null ? null
                        : form.getPortList().stream()
                        .map(p -> com.csm.paas.core.domain.software.model.valobj.UpdatePortItemVO.builder()
                                .containerPort(p.getContainerPort()).protocol(p.getProtocol()).name(p.getName())
                                .port(p.getPort()).targetPort(p.getTargetPort()).build())
                        .toList())
                .build();
        return ResponseDTO.ok(serviceQueryService.updateServiceDiscovery(namespace, vo));
    }

    /**
     * 厂商 `GET /v1/service/baseComponent?nodeName=`：kube-system 基础组件列表。
     *
     * <p>固定顺序 `weave-net` / `kube-proxy` / `kube-keepalived-vip`；每项两个键 `daemonSet` 与 `v1PodList`；
     * `kube-proxy` 的 pod 用 `k8s-app=` 选择器（另两个用 `name=`）；keepalived 没有 pod 时**整条不出现**。
     * 规则见 `IServiceQueryService.queryBaseComponents`（规格 `findings/128`）。
     */
    @GetMapping("/v1/service/baseComponent")
    public ResponseDTO<java.util.List<java.util.Map<String, Object>>> baseComponent(
            @org.springframework.web.bind.annotation.RequestParam String nodeName) {
        return ResponseDTO.ok(serviceQueryService.queryBaseComponents(nodeName));
    }

    /**
     * 厂商 `PUT /v1/service/batch-restart`：批量重启服务（body 是**服务名数组**）。
     *
     * <p>Deployment：先按 `system/svcName` 找第一个 pod 名写一条 `g_pod_event`（`restartNum = max(1, 历史最大值)`），
     * 再给 pod 模板打 `kubectl.kubernetes.io/restartedAt` 注解并整份 replace；StatefulSet 只做后者。
     * 规则见 `IServiceQueryService.batchRestartServices`（规格 `findings/127`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/batch-restart")
    public ResponseDTO<Boolean> batchRestart(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<String> serviceNames) {
        return ResponseDTO.ok(serviceQueryService.batchRestartServices(namespace, serviceNames));
    }

    /**
     * 厂商 `PUT /v1/service/batch-stop`：批量停止服务（body 是**服务名数组**）。
     *
     * <p>逐个：读 Deployment（回退 StatefulSet，都没有则跳过）；`spec.replicas == 0` 也跳过；
     * 否则整份 replace 把副本数设成 0。规则见 `IServiceQueryService.batchStopServices`（规格 `findings/126`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/batch-stop")
    public ResponseDTO<Boolean> batchStop(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<String> serviceNames) {
        return ResponseDTO.ok(serviceQueryService.batchStopServices(namespace, serviceNames));
    }

    /**
     * 厂商 `PUT /v1/service/batch-start`：批量启动服务（body 是**服务名数组**）。
     *
     * <p>与批量停止不同：不看 `spec.replicas`，而是按 `status` 推状态串，**只有 `STOP` 才启动**。
     * 规则见 `IServiceQueryService.batchStartServices`（规格 `findings/126`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/batch-start")
    public ResponseDTO<Boolean> batchStart(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<String> serviceNames) {
        return ResponseDTO.ok(serviceQueryService.batchStartServices(namespace, serviceNames));
    }

    /**
     * 厂商 `GET /v1/service/baseEvent?baseServiceName=`：基础服务（kube-system 组件）的 Event 列表。
     *
     * <p>命名空间写死 `kube-system`；`kube-proxy` 用 `k8s-app` 选择器、其它用 `name`；ReplicaSet 名直接用
     * `<baseServiceName>-`。规则见 `IServiceQueryService.queryBaseServiceEvents`（规格 `findings/125`）。
     */
    @GetMapping("/v1/service/baseEvent")
    public ResponseDTO<java.util.List<java.util.Map<String, Object>>> baseEvent(
            @org.springframework.web.bind.annotation.RequestParam String baseServiceName) {
        return ResponseDTO.ok(serviceQueryService.queryBaseServiceEvents(baseServiceName));
    }

    /**
     * 厂商 `POST /v1/service/tolerance?serviceName&type`：开启/关闭 master 节点污点容忍。
     *
     * <p>`type=0` 关闭（本来就为空则不打 patch）、`type=1` 开启（固定那一条容忍）、**其它 type ⇒ `data:false`**。
     * 规则与那个"拼重了的 i18n 键"见 `IServiceQueryService.allowMasterTolerance`（规格 `findings/124`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/tolerance")
    public ResponseDTO<Boolean> tolerance(
            @org.springframework.web.bind.annotation.RequestParam String serviceName,
            @org.springframework.web.bind.annotation.RequestParam Integer type) {
        return ResponseDTO.ok(serviceQueryService.allowMasterTolerance(namespace, serviceName, type));
    }

    /**
     * 厂商 `GET /v1/service/event?name=`：某服务的 Event 事件列表。
     *
     * <p>取第一个 pod 的 `generateName` 去末位字符当 ReplicaSet 名，分别查 ReplicaSet 与每个 Pod 的事件，
     * 按 `lastTimestamp` 降序（空值排最前）。规则见 `IServiceQueryService.queryServiceEvents`（规格 `findings/123`）。
     */
    @GetMapping("/v1/service/event")
    public ResponseDTO<java.util.List<java.util.Map<String, Object>>> serviceEvent(
            @org.springframework.web.bind.annotation.RequestParam String name) {
        return ResponseDTO.ok(serviceQueryService.queryServiceEvents(namespace, name));
    }

    /**
     * 厂商 `PUT /v1/service/rollingUpdate`：滚动更新 / 更新镜像版本（请求体 `ServiceImageForm`）。
     *
     * <p>`type` 1 ⇒ `maxSurge=1/maxUnavailable=0`、2 ⇒ `0/1`、其它值不改策略；另外写
     * `gaop/updateTimestamp`、`minReadySeconds=2`、`containers[0].image`；成功后再推进关联软件的 `updated_at`。
     * 规则见 `IServiceQueryService.updateServiceImageRollingUpdate`（规格 `findings/122`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/rollingUpdate")
    public ResponseDTO<Boolean> rollingUpdate(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ServiceImageDTO form) {
        return ResponseDTO.ok(serviceQueryService.updateServiceImageRollingUpdate(namespace,
                com.csm.paas.core.domain.software.model.valobj.ServiceImageVO.builder()
                        .name(form == null ? null : form.getName())
                        .type(form == null ? null : form.getType())
                        .version(form == null ? null : form.getVersion())
                        .build()));
    }

    /**
     * 厂商 `PUT /v1/service/healthCheck`：设置/清空第一个容器的 readiness 与 liveness 探针。
     *
     * <p>`type` 0=清空、1=HTTPGet（scheme 写死 HTTP）、2=TCPSocket、其它值不动；返回 `data:true`。
     * 规则见 `IServiceQueryService.updateHealthCheck`（规格 `findings/121`）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/healthCheck")
    public ResponseDTO<Boolean> healthCheck(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.HealthCheckDTO form) {
        return ResponseDTO.ok(serviceQueryService.updateHealthCheck(namespace,
                com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.builder()
                        .name(form == null ? null : form.getName())
                        .readiness(toHealthSet(form == null ? null : form.getReadiness()))
                        .liveness(toHealthSet(form == null ? null : form.getLiveness()))
                        .build()));
    }

    private static com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO toHealthSet(
            com.csm.paas.core.api.dto.HealthCheckDTO.HealthSetDTO dto) {
        if (dto == null) {
            return null;
        }
        return com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO.builder()
                .type(dto.getType()).path(dto.getPath()).port(dto.getPort())
                .initialDelaySeconds(dto.getInitialDelaySeconds())
                .timeoutSeconds(dto.getTimeoutSeconds())
                .periodSeconds(dto.getPeriodSeconds())
                .successThreshold(dto.getSuccessThreshold())
                .failureThreshold(dto.getFailureThreshold())
                .build();
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
     * 厂商 `POST /v1/service/{service}/updateVolumes`：创建/编辑服务的文件映射与文件挂载（configmap、hostpath）。
     *
     * <p>**整份替换** `spec.volumes`（不是合并）且只把挂载写给**第一个容器**；返回 replace 后的 Deployment。
     * 规则与四处怪点见 `IServiceQueryService.updateServiceVolumes`（规格 `findings/119`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/{service}/updateVolumes")
    public ResponseDTO<java.util.Map<String, Object>> updateVolumes(
            @org.springframework.web.bind.annotation.PathVariable String service,
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.UpdateServiceVolumesDTO form) {
        var vo = com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO.builder()
                .configMapList(form.getConfigMapList() == null ? null
                        : form.getConfigMapList().stream()
                        .map(i -> com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO.ConfigMapItemVO
                                .builder().dir(i.getDir()).configmap(i.getConfigmap()).keys(i.getKeys()).build())
                        .toList())
                .hostPathList(form.getHostPathList() == null ? null
                        : form.getHostPathList().stream()
                        .map(i -> com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO.HostPathItemVO
                                .builder().hostPath(i.getHostPath()).podPath(i.getPodPath())
                                .readOnly(i.getReadOnly()).build())
                        .toList())
                .build();
        return ResponseDTO.ok(serviceQueryService.updateServiceVolumes(namespace, service, vo));
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

    /**
     * 厂商 `GET /v1/service/{serviceName}`：服务详情（数据结构与列表项"一致"，实则多三个键）。
     *
     * <p>规则与两处主动收紧见 {@link IServiceQueryService#queryServiceDetail}；
     * 服务不存在 ⇒ 400 `service.service.name.not.found`。
     * <p><b>路由注意</b>：这个 `{serviceName}` 变量映射会吃掉所有未匹配的 `/v1/service/<x>` 子路径
     * （Spring 优先精确匹配，所以 `/labels`、`/gaop`、`/baseEvent` 等仍走各自的方法）。
     */
    @GetMapping("/v1/service/{serviceName}")
    public ResponseDTO<com.csm.paas.core.api.dto.ServiceDetailDTO> serviceDetail(
            @org.springframework.web.bind.annotation.PathVariable String serviceName) {
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.ServiceListAssembler.toDetailResponse(
                serviceQueryService.queryServiceDetail(namespace, serviceName)));
    }

    /**
     * 厂商 `PUT /v1/service/updateServiceRole?serviceKey&name`：更新服务关联其他服务标识的权限。
     *
     * <p>两个参数都是 **query parameter**；规则（含厂商那段"put 被覆盖"的缺陷）见
     * {@link IServiceQueryService#updateServiceRole}。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/updateServiceRole")
    public ResponseDTO<Boolean> updateServiceRole(
            @org.springframework.web.bind.annotation.RequestParam String serviceKey,
            @org.springframework.web.bind.annotation.RequestParam String name) {
        return ResponseDTO.ok(serviceQueryService.updateServiceRole(namespace, serviceKey, name));
    }

    /**
     * 厂商 `POST /v1/service/logs`：服务历史日志（请求体 `ServiceLogForm`）。
     *
     * <p>走 Loki：时间跨度过大 ⇒ 400 `query.time.range.must.between.720`；
     * 日志服务没给出可用响应 ⇒ **`data:null`**（厂商 `Response.ok(null)`）；
     * 否则是 `{podLogs, count}`（`pod → container` 两级聚合）。规则见
     * {@link IServiceQueryService#queryServiceHistoryLogJson}（**类 C**）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/logs")
    public ResponseDTO<com.csm.paas.core.api.dto.HistoryLogDTO> serviceLogs(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ServiceLogFormDTO form) {
        String raw = serviceQueryService.queryServiceHistoryLogJson(
                form == null ? null : form.getServiceName(),
                form == null ? null : form.getStartTime(),
                form == null ? null : form.getEndTime(),
                form == null ? null : form.getSize(),
                form == null ? null : form.getKeyword());
        if (raw == null) {
            return ResponseDTO.ok(null);
        }
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.LokiLogAssembler.toHistoryLog(raw));
    }

    /**
     * 厂商 `GET /v1/service/imageStorage`：镜像存储大小（`{free,total}`，字节）。
     *
     * <p>SSH 到节点跑 `df -h <mount.path> | awk 'NR==2 {print $2, $4}'` 并把两列换算成字节；
     * **连不上或命令异常 ⇒ `{free:0,total:0}`**（厂商不报错）。k8s/Harbor 分支未实现（本环境无 Harbor）。
     * 规则见 {@link IServiceQueryService#queryImageStorage}（**类 C**）。
     */
    @GetMapping("/v1/service/imageStorage")
    public ResponseDTO<com.csm.paas.core.api.dto.StorageInfoDTO> imageStorage() {
        var vo = serviceQueryService.queryImageStorage(mountPath);
        return ResponseDTO.ok(com.csm.paas.core.api.dto.StorageInfoDTO.builder()
                .free(vo.getFree()).total(vo.getTotal()).build());
    }

    /**
     * 厂商 `PUT /v1/service/schedulingNode?name&type`：节点调度（type 0 停止 / 1 启动）。
     *
     * <p><b>破坏性</b>：SSH 到 master 执行 `kubectl cordon|uncordon <name>`。
     * 列节点失败、SSH 连不上都返回 `data:false`；节点名不存在 ⇒ 400 `service.node.name.not.found`。
     * 规则见 {@link com.csm.paas.core.domain.software.service.IServiceOpsService#schedulingNode}（**类 C**）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/schedulingNode")
    public ResponseDTO<Boolean> schedulingNode(
            @org.springframework.web.bind.annotation.RequestParam String name,
            @org.springframework.web.bind.annotation.RequestParam Integer type) {
        return ResponseDTO.ok(serviceOpsService.schedulingNode(name, type));
    }

    /**
     * 厂商 `POST /v1/service/reStartBaseService?podName`：重启 kube-system 基础服务组件。
     *
     * <p><b>破坏性</b>：SSH 到 master 执行
     * `kubectl delete pod <podName> -n kube-system --force --grace-period=0`。
     * 列节点失败、SSH 连不上都返回 `data:false`；命令失败 ⇒ 400 `ssh.delete.base.service.pod.failed`。
     * 规则见 {@link com.csm.paas.core.domain.software.service.IServiceOpsService#restartBaseService}（**类 C**）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/service/reStartBaseService")
    public ResponseDTO<Boolean> reStartBaseService(
            @org.springframework.web.bind.annotation.RequestParam String podName) {
        return ResponseDTO.ok(serviceOpsService.restartBaseService(podName));
    }

    /**
     * 厂商 `DELETE /v1/service/servicePods`：批量删除服务的 Pod 容器（body `{podNames,namespace,force}`）。
     *
     * <p><b>破坏性</b>：直接删 Pod；`force=true` ⇒ `gracePeriodSeconds=0`。
     * 任一删除失败 ⇒ 500 `service.delete.pod.failed`。规则见
     * {@link com.csm.paas.core.domain.software.service.IServiceOpsService#deletePods}。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/v1/service/servicePods")
    public ResponseDTO<Boolean> deleteServicePods(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.DeletePodsDTO form) {
        return ResponseDTO.ok(serviceOpsService.deletePods(
                form == null ? null : form.getNamespace(),
                form == null ? null : form.getPodNames(),
                form == null ? null : form.getForce()));
    }

    @GetMapping("/v1/service")
    public ResponseDTO<ServiceListResponseDTO> list(HttpServletRequest request) {
        // 厂商在这两个端点**不做**应用级校验（实测无 cookie 也 200），
        // 鉴权由 gateway-auth 在边缘完成，见 docs/findings/14-vendor-auth-is-gateway-side.md

        ServiceListEntity entity = serviceQueryService.queryServiceList(namespace);
        return ResponseDTO.ok(ServiceListAssembler.toResponse(entity));
    }

    // ================================================================ 最后一批端点（node / patch / serviceInfoByNode）

    /**
     * 厂商 `DELETE /v1/service/node?nodeName=`：删节点。
     *
     * <p><b>破坏性</b>：直接 `coreV1Api.deleteNode(nodeName, …)`，照抄厂商**不做任何前置校验**；
     * 任何失败 ⇒ 400 `alert.delete.node.info.error`；成功恒 `data:true`。
     * 规则见 {@link IServiceQueryService#deleteNode}（**照抄未做在线实验**）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/v1/service/node")
    public ResponseDTO<Boolean> deleteNode(
            @org.springframework.web.bind.annotation.RequestParam String nodeName) {
        return ResponseDTO.ok(serviceQueryService.deleteNode(nodeName));
    }

    /**
     * 厂商 `PUT /v1/service/patch`：以 YAML 方式导入服务配置（请求体是 `ServicesItem` 的三个快照）。
     *
     * <p><b>破坏性</b>：replace Deployment + patch/创建/删除 `<name>-nodeport` + patch 主 Service；
     * Deployment 不存在时还会先创建一个。规则与照抄点（含那段 `spec.strategy` 被清空后永不执行的
     * `%` 归一化）见 {@link IServiceQueryService#updateServiceConfig}（**照抄未做在线实验**）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/service/patch")
    public ResponseDTO<Boolean> updateServiceConfig(
            @org.springframework.web.bind.annotation.RequestBody com.csm.paas.core.api.dto.ServicePatchDTO item) {
        return ResponseDTO.ok(serviceQueryService.updateServiceConfig(namespace,
                item == null ? null : item.getDeployment(),
                item == null ? null : item.getService(),
                item == null ? null : item.getNodePortService()));
    }

    /**
     * 厂商 `GET /v1/service/serviceInfoByNode?nodeName&page&size&keyword&order`：按节点查服务列表。
     *
     * <p>`keyword` 长度 &gt; 45 ⇒ 400 `service.invalid.service.name.length`；
     * 取 master 节点名失败 ⇒ 400 `k8s.get.node.info.failed`；
     * 分页 `runningDeployment` 恒 0、`size` 大于总数时忽略 page。
     * 规则见 {@link IServiceQueryService#queryServiceInfoByNode}（**类 C**：监控补齐依赖 Prometheus）。
     *
     * <p>列表项复用 {@code ServiceDetailDTO}（厂商 `ServicesItem` 的 20 键，`*Order` 是 Double），
     * 再覆盖本端点自己算的 `restartCount`/`podInfoList`/`belongNodeList`/三个 order。
     */
    @GetMapping("/v1/service/serviceInfoByNode")
    public ResponseDTO<com.csm.paas.core.api.dto.ServiceInfoByNodeResponseDTO> serviceInfoByNode(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String nodeName,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "1") Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "20") Integer size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer order) {
        if (keyword != null && keyword.length() > 45) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.invalid.service.name.length");
        }
        var vo = serviceQueryService.queryServiceInfoByNode(namespace, nodeName, keyword, order,
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size));
        java.util.List<com.csm.paas.core.api.dto.ServiceDetailDTO> services = new java.util.ArrayList<>();
        for (var item : vo.getServices()) {
            var dto = ServiceListAssembler.toDetailResponse(item.getRuntime());
            dto.setRestartCount(item.getRestartCount());
            dto.setCpuOrder(item.getCpuOrder());
            dto.setMemoryOrder(item.getMemoryOrder());
            dto.setGpuOrder(item.getGpuOrder());
            dto.setPodInfoList(item.getPodInfoList());
            dto.setBelongNodeList(item.getBelongNodeList());
            services.add(dto);
        }
        return ResponseDTO.ok(com.csm.paas.core.api.dto.ServiceInfoByNodeResponseDTO.builder()
                .runningDeployment(vo.getRunningDeployment())
                .total(vo.getTotal())
                .count(vo.getCount())
                .services(services)
                .build());
    }
}
