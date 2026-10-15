package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.valobj.GaopServiceFilter;
import com.csm.paas.core.domain.software.valobj.GaopServiceNames;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 只读领域服务：把运行态服务聚合成列表。
 * <p>注意 {@code runningDeployment} 的语义在厂商侧尚未确认（运行时样本里 9 个服务、5 个 Pod 在跑，
 * 厂商却给 0）。这里先用"就绪副本 ≥ 1"作为工作定义，并把差异记进工单 06 的结论。
 */
@Slf4j
@Service
public class ServiceQueryServiceImpl implements IServiceQueryService {

    /** 厂商 `getNodePortUsedList` 里写死的条目名（http/https 两条都用它）。 */
    private static final String FRAMEWORK_INTERNAL = "框架内部";

    @Resource
    private IServiceRuntimePort serviceRuntimePort;

    @Resource
    private IServiceAuthRepository serviceAuthRepository;

    /** Pod 重启记录来自 `g_pod_event`（软件域仓储里已有的 dao 也能查，但归属放这里更清晰）。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository podEventRepository;

    /** i18n 自动名落在 `g_setting`（键形如 `i18n.service.<lang>.<serviceKey>`）。 */
    @Resource
    private com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository i18nRepository;

    /** `patchService` 的 B 分支要按 `g_software_app.app_name` 找软件。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository softwareRepository;

    /** `patchService` 的 B 分支写完 `g_service_port` 后通知网关刷新。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort gatewayRefreshPort;

    /** `relationService` 要按当前语言查 i18n 自动名。 */
    @Resource
    private com.csm.paas.core.domain.language.service.ILanguageQueryService languageQueryService;

    /** `GET /v1/service/imageStorage` 要 SSH 到节点跑 `df -h`（厂商 `K3sImageServiceImpl`）。 */
    @Resource
    private com.csm.paas.core.domain.remote.adapter.port.ISshPort sshPort;

    /** `POST /v1/service/logs` 走 Loki（厂商 `TenxCloudImpl.getHistoryLog`）。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.port.ILokiLogPort lokiLogPort;

    /** `POST /v1/software/top` 走 Prometheus（厂商 `SoftwareService.getResourceMetric`）。 */
    @Resource
    private com.csm.paas.core.domain.software.adapter.port.IPrometheusMetricPort prometheusMetricPort;

    /** `PUT /v1/service/patch` 的镜像 host 替换用（厂商 `dockerConfig.getRegistryHost()`）。 */
    @Resource
    private com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort dockerRegistryPort;

    /** 厂商 `node.label`（`application.yml:194-195`，默认 `control-plane`）：找 master 节点的标签后缀。 */
    @Value("${node.label:control-plane}")
    private String nodeLabel;

    /** 厂商 `K8s.NAMESPACE`（`/v1/software/top` 的 `namespace` 参数为空时的兜底）。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String defaultNamespace;

    /** 基础服务组件所在的命名空间（厂商在多处**写死** `kube-system`）。 */
    private static final String KUBE_SYSTEM_NAMESPACE = "kube-system";

    /** 厂商 `AuthServiceType` 的取值（`constant/AuthServiceType.java`）。 */
    private static final Integer AUTH_AUTHORIZED = 1;
    private static final Integer AUTH_EXPIRED = 2;

    /** 厂商 `allowMasterTolerance` 里那条固定的 master 容忍。 */
    private static final String MASTER_TAINT_KEY = "node-role.kubernetes.io/master";

    /**
     * 开启/关闭 master 污点容忍（厂商 `allowMasterTolerance:3425-3475`，规格见 `docs/findings/124`）。
     */
    @Override
    public boolean allowMasterTolerance(String namespace, String serviceName, Integer type) {
        // 厂商：读不到 Deployment ⇒ 400，键是源码里**拼重了的字面量**（消息包查不到 ⇒ 回退成键本身，文案一致）
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.not.found.update.taintservice.not.found.update.taint", serviceName);
        }
        if (type != null && type == 0) {
            // 厂商：tolerations 本来就为空 ⇒ **直接返回 true，不打 patch**
            if (serviceRuntimePort.readDeploymentTolerations(namespace, serviceName).isEmpty()) {
                return true;
            }
            patchTolerations(namespace, serviceName, List.of());
            return true;
        }
        if (type != null && type == 1) {
            patchTolerations(namespace, serviceName, List.of(
                    com.csm.paas.core.domain.software.model.valobj.TolerationVO.builder()
                            .key(MASTER_TAINT_KEY).operator("Exists").effect("NoSchedule").build()));
            return true;
        }
        // 其它 type：厂商 `Response.ok(false)` ⇒ HTTP 200 + data:false，且**不打 patch**
        return false;
    }

    private void patchTolerations(String namespace, String serviceName,
                                  List<com.csm.paas.core.domain.software.model.valobj.TolerationVO> tolerations) {
        try {
            serviceRuntimePort.patchDeploymentTolerations(namespace, serviceName, tolerations);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("更新服务 {} 的污点容忍时出现异常：{}", serviceName, e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.update.service.failed", serviceName);
        }
    }

    /**
     * 某服务的 Event 列表（厂商 `getServiceEvent:2821-2859`，规格见 `docs/findings/123`）。
     */
    @Override
    public List<Map<String, Object>> queryServiceEvents(String namespace, String serviceName) {
        try {
            List<com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO> pods =
                    serviceRuntimePort.listPodsByLabel(namespace, "system/svcName", serviceName);
            List<String> podPreNames = new ArrayList<>();
            List<String> podNames = new ArrayList<>();
            for (var pod : pods) {
                // 厂商：`generateName.substring(0, len-1)`——没有判空 ⇒ null 时 NPE ⇒ 500。
                // 非控制器创建的 pod 没有 generateName，所以这里跳过它（deviations B18）。
                if (pod.getGenerateName() == null || pod.getGenerateName().isEmpty()) {
                    log.warn("pod {} 没有 generateName，跳过（厂商此处会 NPE）", pod.getName());
                    continue;
                }
                podPreNames.add(pod.getGenerateName().substring(0, pod.getGenerateName().length() - 1));
                podNames.add(pod.getName());
            }
            if (podPreNames.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> events = new ArrayList<>(
                    serviceRuntimePort.listEvents(namespace, podPreNames.get(0), "ReplicaSet"));
            for (String podName : podNames) {
                events.addAll(serviceRuntimePort.listEvents(namespace, podName, "Pod"));
            }
            // 厂商 `Comparator.comparing(lastTimestamp, nullsLast(natural)).reversed()`
            // ⇒ 非空按时间**降序**，而 `.reversed()` 把"空值最后"也翻了 ⇒ **空值排最前**（照抄）
            events.sort((a, b) -> {
                var ta = eventTimestamp(a);
                var tb = eventTimestamp(b);
                if (ta == null && tb == null) {
                    return 0;
                }
                if (ta == null) {
                    return -1;
                }
                if (tb == null) {
                    return 1;
                }
                return tb.compareTo(ta);
            });
            return events;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("出现异常", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.get.service.event.info.exception");
        }
    }

    /** 事件快照里的 `lastTimestamp`（ISO 串）→ 可比较的瞬时；缺失或解析不了按 null（排最前）。 */
    private static java.time.OffsetDateTime eventTimestamp(Map<String, Object> event) {
        Object value = event.get("lastTimestamp");
        if (!(value instanceof String str) || str.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(str);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public boolean updateServiceImageRollingUpdate(String namespace,
                                                   com.csm.paas.core.domain.software.model.valobj.ServiceImageVO form) {
        String serviceName = form == null ? null : form.getName();
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.not.found.update.image.version", serviceName);
        }
        long updateTime = System.currentTimeMillis();
        try {
            serviceRuntimePort.patchDeploymentRollingUpdate(namespace, serviceName,
                    form == null ? null : form.getType(),
                    form == null ? null : form.getVersion(),
                    updateTime);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "k8s.call.api.failed.to.change.image.version");
        }
        // 厂商：patch 成功**之后**才 `updateSoftwareTimeByService`（失败时不动库）
        for (String softwareId : softwareRepository.querySoftwareIdsByAppName(serviceName)) {
            softwareRepository.updateSoftwareUpdatedAt(softwareId, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public boolean updateServiceName(String namespace, String serviceKey, String name, String nameUs, String nameFr) {
        try {
            serviceRuntimePort.patchDeploymentServiceLabel(namespace, serviceKey, name);
        } catch (Exception e) {
            log.error("修改服务名称失败（Deployment），回退 StatefulSet", e);
            try {
                serviceRuntimePort.patchStatefulSetServiceLabel(namespace, serviceKey, name);
            } catch (Exception ex) {
                throw new com.csm.paas.core.types.exception.SystemException("service.modify.name.failed");
            }
        }
        // 厂商两条路径成功时都写 i18n 自动名（`g_setting['i18n.service.<lang>.<key>']`）
        i18nRepository.upsertValue("i18n.service.en_US." + serviceKey, nameUs, "service name en_US");
        i18nRepository.upsertValue("i18n.service.fr_FR." + serviceKey, nameFr, "service name fr_FR");
        return true;
    }

    @Override
    public ServiceListEntity queryGaopServiceList(String namespace, int page, int size, Integer type, String keyword) {
        List<ServiceRuntimeEntity> all = serviceRuntimePort.queryServices(namespace).stream()
                .filter(s -> GaopServiceFilter.matches(type, s.getName(), s.getAppType(), s.getAppName()))
                .peek(s -> s.setFixedName(GaopServiceNames.fixedName(s.getName())))
                .collect(Collectors.toList());

        if (keyword != null) {
            // 厂商两段式：先按 deployment 名 contains；若为空，再用 filterServiceName 兜底一次
            List<ServiceRuntimeEntity> byName = all.stream()
                    .filter(s -> s.getName() != null && s.getName().contains(keyword))
                    .collect(Collectors.toList());
            all = byName.isEmpty() ? all.stream().filter(s -> filterServiceName(keyword, s)).collect(Collectors.toList()) : byName;
        }

        int total = all.size();
        int from = Math.max(0, com.csm.paas.core.types.util.PageUtils.offset(page, size));
        int to = Math.min(total, from + size);
        List<ServiceRuntimeEntity> pageItems = from >= total ? List.of() : all.subList(from, to);

        int running = (int) pageItems.stream().filter(s -> s.getReplicas() != null && s.getReplicas() > 0).count();
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(total)
                .count(pageItems.size())
                .services(pageItems)
                .build();
    }

    /**
     * 厂商 `ServiceController.filterServiceName:2070-2081` 的**精确实现**：
     * 取 pod 模板注解 `gaop/serviceLabel`，**大小写敏感的 contains**；注解或该键缺失 → false。
     * <p>注意它不是匹配 i18n 名、也不是匹配 `fixedName`——第一版我按"名字/显示名忽略大小写包含"实现，
     * 是**错的**（`findings/56`）。
     */
    private static boolean filterServiceName(String keyword, ServiceRuntimeEntity s) {
        String label = s.getServiceLabel();
        return label != null && label.contains(keyword);
    }

    @Override
    public boolean updateServiceCommand(String namespace, String serviceName,
                                        List<String> command, List<String> args) {
        if (!serviceRuntimePort.patchDeploymentCommand(namespace, serviceName, command, args)) {
            // 厂商：读不到 Deployment → WrongArgumentException（**没有** StatefulSet 回退）
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.service.name.not.found", serviceName);
        }
        return true;
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO> queryRestartInfo(String serviceName) {
        // 厂商用 serviceName 作为**请求参数**（不是 body/路径）；查库在仓储里，这里只做归属
        return podEventRepository.queryPodRestartInfo(serviceName);
    }

    @Override
    public boolean cancelServiceStartup(String namespace, List<String> serviceNames) {
        for (String serviceName : serviceNames) {
            if (!serviceRuntimePort.cancelDeploymentStartup(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.get.service.config.exception.cancel.delay", serviceName);
            }
        }
        return true;
    }

    @Override
    public boolean setServiceStartup(String namespace, List<String> serviceNames, long time,
                                     String harborHost, String initImage) {
        if (time < 0L) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.negative.startup.delay");
        }
        for (String serviceName : serviceNames) {
            if (!serviceRuntimePort.setDeploymentStartup(namespace, serviceName, time, harborHost, initImage)) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.get.service.config.exception.set.delay", serviceName);
            }
        }
        return true;
    }

    @Override
    public boolean updateServiceNodePort(String namespace, String serviceName, String type,
                                         List<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO> nodePorts) {
        String effectiveType = (type == null || type.isBlank()) ? "ClusterIP" : type;   // 厂商默认值
        List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> specs = new ArrayList<>();

        if ("NodePort".equals(effectiveType) && (nodePorts == null || nodePorts.isEmpty())) {
            // A 分支：从 Deployment 的第一个容器端口推导
            var containerPorts = serviceRuntimePort.readDeploymentContainerPorts(namespace, serviceName);
            if (containerPorts == null) {
                // 厂商：消息本身就是参数（带服务名的中文），走 SystemException（500/-1）
                throw new com.csm.paas.core.types.exception.SystemException(
                        "设置NodePort失败：该服务" + serviceName + "无法获取deployment信息");
            }
            if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.SystemException("设置NodePort失败：service为空");
            }
            if (containerPorts.isEmpty()) {
                return false;   // 厂商：ports 为空 → 什么都不做，v1Service 为 null ⇒ data:false
            }
            for (int i = 0; i < containerPorts.size(); i++) {
                var cp = containerPorts.get(i);
                specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                        .appProtocol(cp.getProtocol())
                        .name((cp.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                        .port(cp.getContainerPort())
                        .protocol(cp.getProtocol())
                        .targetPort(cp.getContainerPort())
                        .build());
            }
        } else {
            // B 分支：按入参构建
            if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.SystemException("设置NodePort失败：service为空");
            }
            if ("NodePort".equals(effectiveType) && nodePorts != null) {
                for (int i = 0; i < nodePorts.size(); i++) {
                    var np = nodePorts.get(i);
                    specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                            .appProtocol(np.getProtocol())
                            .name((np.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                            .nodePort(np.getNodePort())
                            .port(np.getPort())
                            .protocol(np.getProtocol())
                            .targetPort(np.getPort())
                            .build());
                }
            }
        }
        try {
            return serviceRuntimePort.patchServicePorts(namespace, serviceName, effectiveType, specs) != null;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("422") && msg.contains("provided port is already allocated")) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException("service.port.occupied");
            }
            throw new com.csm.paas.core.types.exception.SystemException("service.update.nodeport.failed");
        }
    }

    @Override
    public boolean updateServiceNodePortsEndpoint(String namespace, String serviceName, String type,
                                                  List<com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO> nodePorts) {
        String effectiveType = (type == null || type.isBlank()) ? "ClusterIP" : type;

        // 1) 主 Service 必须存在
        if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
            throw new com.csm.paas.core.types.exception.SystemException("设置NodePort失败：service为空");
        }
        // 2-3) 读工作负载（Deployment 优先，回退 StatefulSet），用容器端口推导主 Service 的 ports
        var containerPorts = serviceRuntimePort.readWorkloadContainerPorts(namespace, serviceName);
        if (containerPorts == null) {
            throw new com.csm.paas.core.types.exception.SystemException(
                    "设置NodePort失败：该服务" + serviceName + "无法获取deployment信息");
        }
        List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> mainSpecs = new ArrayList<>();
        for (int i = 0; i < containerPorts.size(); i++) {
            var cp = containerPorts.get(i);
            mainSpecs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                    .appProtocol(cp.getProtocol())
                    .name((cp.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                    .port(cp.getContainerPort())
                    .protocol(cp.getProtocol())
                    .targetPort(cp.getContainerPort())
                    .nodePort(null)   // 厂商在步骤 3 里**显式置 null**
                    .build());
        }
        // 4-5) 主 Service 的 type 写死 ClusterIP，然后 merge-patch（ports 为空时不改 ports，照抄）
        serviceRuntimePort.patchServicePorts(namespace, serviceName, "ClusterIP",
                mainSpecs.isEmpty() ? List.of() : mainSpecs);

        // 6) 只有请求 type 是 ClusterIP 才继续处理 <name>-nodeport
        if (!"ClusterIP".equals(effectiveType)) {
            return false;
        }
        String nodePortServiceName = serviceName + "-nodeport";
        boolean exists = serviceRuntimePort.serviceExists(namespace, nodePortServiceName);
        // 复制一份再改：端口分配要往里面加值，不能依赖 port 返回的集合可写
        List<Integer> used = new ArrayList<>(serviceRuntimePort.listUsedNodePorts(namespace));

        List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> specs = new ArrayList<>();
        if (nodePorts != null) {
            for (int i = 0; i < nodePorts.size(); i++) {
                var np = nodePorts.get(i);
                Integer port = np.getNodePort();
                if (port == null) {
                    port = randomFreeNodePort(used);   // 厂商：随机 [10000, 75534]，≤10 次
                    used.add(port);
                }
                specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                        .appProtocol(np.getProtocol())
                        .name((np.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                        .nodePort(port)
                        .port(np.getPort())
                        .protocol(np.getProtocol())
                        .targetPort(np.getPort())
                        .build());
            }
        }
        try {
            if (exists) {
                serviceRuntimePort.patchNodePortServicePorts(namespace, nodePortServiceName, specs);
            } else {
                serviceRuntimePort.createNodePortService(namespace, nodePortServiceName, effectiveType, specs, serviceName);
            }
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            // 厂商：K8s ApiException → service.port.occupied.error（**消息其实会误导**：
            // 我们实测这条分支最常见的原因是 `type=ClusterIP` 却带了 nodePort ⇒ K8s 422。
            // 但键名照抄，不改。）
            log.error("创建/更新 {}-nodeport 失败（K8s API）: {}", serviceName, e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.port.occupied.error");
        } catch (Exception e) {
            log.error("创建/更新 {}-nodeport 失败: {}", serviceName, e.getMessage(), e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.create.node.port.failed");
        }
        return true;
    }

    @Override
    public boolean updateServiceDiscovery(String namespace,
                                          com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO form) {
        String serviceName = form == null ? null : form.getServiceName();
        if (!"Service".equals(form == null ? null : form.getType())) {
            return updateServiceDiscoveryPorts(namespace, form, serviceName);
        }
        try {
            // 1) Service 必须存在。厂商 fabric8/openapi 的 `readNamespacedService` 对 404 **抛异常**（不是返回 null），
            //    被外层 `catch (ApiException)` 接住 ⇒ 实际响应是 400 `k8s.call.api.failed`；
            //    源码里那句「设置NodePort失败：service为空」是**死代码**（findings/118 §2.1）。
            if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "service " + serviceName + " not found");
            }
            // 2) 工作负载（Deployment 优先，回退 StatefulSet）第一个容器的端口
            var containerPorts = serviceRuntimePort.readWorkloadContainerPorts(namespace, serviceName);
            if (containerPorts == null) {
                // 厂商这里抛 SystemException，但它在外层 `catch (Exception)` 之内 ⇒ 被吞成 400
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.update.service.discovery.failed");
            }
            // 3) 用容器端口重建 Service 的 ports（name 小写拼接、nodePort 显式置 null）
            List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> specs = new ArrayList<>();
            for (int i = 0; i < containerPorts.size(); i++) {
                var cp = containerPorts.get(i);
                specs.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                        .appProtocol(cp.getProtocol())
                        .name((cp.getProtocol() + "-" + serviceName + "-" + i).toLowerCase())
                        .nodePort(null)
                        .port(cp.getContainerPort())
                        .protocol(cp.getProtocol())
                        .targetPort(cp.getContainerPort())
                        .build());
            }
            // 4) type 写死 ClusterIP，整份 merge-patch
            serviceRuntimePort.patchServicePorts(namespace, serviceName, "ClusterIP", specs);
            return true;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("修改服务发现配置失败（K8s API）: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.call.api.failed");
        } catch (com.csm.paas.core.types.exception.WrongArgumentException e) {
            throw e;   // 上面显式抛的那条（工作负载不存在）
        } catch (Exception e) {
            log.error("修改服务发现配置失败", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.update.service.discovery.failed");
        }
    }

    /**
     * `patchService` 的 **B 分支**（`type != "Service"`，厂商 `updateServiceDiscovery:1088-1162`）：
     * 写 `g_service_port` + patch 工作负载容器端口 + 通知网关。
     *
     * <p>照抄的四处（`docs/findings/118` §3）：
     * <ol>
     *   <li>工作负载读不到 → **500**（厂商这个 `SystemException` 抛在 try **外面**，与 A 分支的 400 形成对照）；</li>
     *   <li>`g_software_app` 为空 ⇒ 不写库、不通知网关，但**仍然 patch 工作负载**（容器端口照样改）；</li>
     *   <li>`path_name = "<deploymentName>-<port>"`、`allow` 恒 true、`web_entry` 恒 false（表单里那两个字段不读）；</li>
     *   <li>所有容器（不只第一个）的 `ports` 都设成同一份列表，且容器端口**只带 protocol 与 containerPort**。</li>
     * </ol>
     */
    private boolean updateServiceDiscoveryPorts(String namespace,
                                                com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO form,
                                                String serviceName) {
        // 1) 工作负载必须存在。厂商在 try **外面**抛 SystemException ⇒ 真的 500
        var ref = serviceRuntimePort.readWorkloadRef(namespace, serviceName);
        if (ref == null) {
            throw new com.csm.paas.core.types.exception.SystemException("修改dployment/statefulSet失败，找不到该服务");
        }
        try {
            if (form.getPortList() == null) {
                // 厂商 `:1108` 直接 `.forEach` ⇒ NPE ⇒ 被 catch(Exception) 吞成 400
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.update.service.details.failed");
            }
            String deploymentName = ref.getName();
            List<String> softwareIds = softwareRepository.querySoftwareIdsByAppName(deploymentName);
            if (!softwareIds.isEmpty()) {
                if (form.getPortList().isEmpty()) {
                    // 厂商 `insertBatch` 是 `<foreach>` 单条 insert：空列表会拼出非法 SQL ⇒ 400。
                    // 显式抛同一个键，而不是靠"制造坏 SQL"来触发（见 `findings/118` §3 第 2 条）。
                    throw new com.csm.paas.core.types.exception.WrongArgumentException(
                            "service.update.service.details.failed");
                }
                for (String softwareId : softwareIds) {
                    softwareRepository.deleteServicePorts(softwareId, deploymentName);
                    List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> rows = new ArrayList<>();
                    for (var item : form.getPortList()) {
                        Integer port = item.getPort();
                        rows.add(com.csm.paas.core.domain.software.model.valobj.ServicePortVO.builder()
                                .softwareId(softwareId)
                                .serviceName(deploymentName)
                                .port(port)
                                .pathName(deploymentName + "-" + port)
                                .webEntry(false)
                                .allow(true)
                                // 厂商是逐行 `System.currentTimeMillis()`，不是一次取一个 now
                                .updatedAt(System.currentTimeMillis())
                                .build());
                    }
                    softwareRepository.insertServicePorts(rows);
                }
                gatewayRefreshPort.refresh();
            }
            // 2) 所有容器的 ports 都换成表单里的（只带 protocol + containerPort）
            List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> containerPorts = form.getPortList()
                    .stream()
                    .map(p -> com.csm.paas.core.domain.software.model.valobj.ContainerPortVO.builder()
                            .protocol(p.getProtocol())
                            .containerPort(p.getContainerPort())
                            .build())
                    .toList();
            serviceRuntimePort.patchWorkloadContainerPorts(namespace, serviceName, ref.isDeployment(), containerPorts);
            return true;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("修改服务配置失败（K8s API）: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.update.service.config.error");
        } catch (com.csm.paas.core.types.exception.WrongArgumentException e) {
            throw e;   // 上面显式抛的两条（portList 为 null / 为空）
        } catch (Exception e) {
            log.error("修改服务详情失败", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.update.service.details.failed");
        }
    }

    /**
     * 创建/编辑服务的文件映射与文件挂载（厂商 `updateServiceVolumes:818-947`，规格见 `docs/findings/119`）。
     */
    @Override
    public Map<String, Object> updateServiceVolumes(
            String namespace, String serviceName,
            com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO form) {
        // 1) Deployment 必须存在。厂商 404 ⇒ 400 `service.service.name.not.found`（**带服务名**）
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.service.name.not.found", serviceName);
        }
        List<com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO> volumes = new ArrayList<>();
        List<com.csm.paas.core.domain.software.model.valobj.VolumeMountVO> volumeMounts = new ArrayList<>();
        try {
            // 2) configMapList：先按 configmap 名**合并**（同名多条合成一个卷，dir 各取各的）
            java.util.LinkedHashMap<String, List<com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO.ConfigMapItemVO>>
                    merged = new java.util.LinkedHashMap<>();
            if (form.getConfigMapList() != null) {
                for (var item : form.getConfigMapList()) {
                    merged.computeIfAbsent(item.getConfigmap(), k -> new ArrayList<>()).add(item);
                }
            }
            for (var entry : merged.entrySet()) {
                String configmap = entry.getKey();
                List<com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO.ConfigMapItemVO> items =
                        entry.getValue();
                String volumeName = "tenxcloud-time-localtime".equals(configmap)
                        ? "tenxcloud-time-localtime"
                        : "configmap-volume-" + randomString(5);
                List<com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO.KeyToPathVO> keyToPaths =
                        new ArrayList<>();
                for (var item : items) {
                    for (String key : item.getKeys()) {
                        // 厂商：items[].path 用的是 **key**（不是 dir）
                        keyToPaths.add(com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO.KeyToPathVO
                                .builder().key(key).path(key).build());
                        // 厂商：挂载路径才是 dir + "/" + key，subPath = key
                        volumeMounts.add(com.csm.paas.core.domain.software.model.valobj.VolumeMountVO.builder()
                                .name(volumeName)
                                .mountPath(item.getDir() + "/" + key)
                                .subPath(key)
                                .build());
                    }
                }
                volumes.add(com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO.builder()
                        .name(volumeName)
                        .configMapName(configmap)
                        .items(keyToPaths)
                        .build());
            }
            // 3) hostPathList
            if (form.getHostPathList() != null) {
                for (var item : form.getHostPathList()) {
                    String volumeName = "/etc/localtime".equals(item.getHostPath())
                            ? "tenxcloud-time-localtime"
                            : "hostpath-volume-" + randomString(5);
                    volumes.add(com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO.builder()
                            .name(volumeName)
                            .hostPath(item.getHostPath())
                            // 厂商：type 是**空串**
                            .hostPathType("")
                            .build());
                    volumeMounts.add(com.csm.paas.core.domain.software.model.valobj.VolumeMountVO.builder()
                            .name(volumeName)
                            .mountPath(item.getPodPath())
                            // 厂商：`readOnly == 2` 才置 true，其它值**连 false 都不写**（所以这里是 null）
                            // 注意厂商写的是 `getReadOnly() == 2`（Integer 拆箱）⇒ readOnly 为 null 时它会 NPE；
                            // 我们按"非只读"处理（deviations B15）。
                            .readOnly(Integer.valueOf(2).equals(item.getReadOnly()) ? Boolean.TRUE : null)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("设置文件映射和文件挂载数据异常", e);
            throw new com.csm.paas.core.types.exception.SystemException("service.file.mapping.or.mount.failed");
        }
        try {
            // 4) 整份 replace（volumes 是替换不是合并；挂载只写第一个容器）
            Map<String, Object> replaced = serviceRuntimePort.replaceDeploymentVolumes(
                    namespace, serviceName, volumes, volumeMounts);
            if (replaced == null) {
                // 上面已判过存在，这里是竞态（对象在两步之间被删）⇒ 厂商会拿到 replace 的 404
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "deployment " + serviceName + " not found");
            }
            return replaced;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("更新配置失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.SystemException("service.update.service.config.failed");
        }
    }

    /**
     * 设置/清空健康检查探针（厂商 `healthCheck:2658-2689`，规格见 `docs/findings/121`）。
     */
    @Override
    public boolean updateHealthCheck(String namespace,
                                     com.csm.paas.core.domain.software.model.valobj.HealthCheckVO form) {
        String serviceName = form == null ? null : form.getName();
        // 厂商：读不到 Deployment ⇒ 400 `service.not.found.update.image.version`（**带服务名**）
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.not.found.update.image.version", serviceName);
        }
        try {
            // 探针的"改不改/改成什么"由 infrastructure 翻译（type 0=清空 1=HTTPGet 2=TCPSocket 其它=不动）
            serviceRuntimePort.patchDeploymentProbes(namespace, serviceName,
                    form == null ? null : form.getReadiness(),
                    form == null ? null : form.getLiveness());
            return true;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "k8s.call.api.failed.to.change.image.version");
        }
        // 注意：容器列表为空时端口抛的是 IndexOutOfBoundsException，**厂商也没兜** ⇒ 让它冒到全局
        // 兜底处理（500 `系统错误`/`code=-1`），所以这里**不**catch Exception。
    }

    /**
     * 服务详情（厂商 `getServiceDetail:1404-1481`，规格见 {@link IServiceQueryService#queryServiceDetail}）。
     */
    @Override
    public ServiceRuntimeEntity queryServiceDetail(String namespace, String serviceName) {
        ServiceRuntimeEntity item = serviceRuntimePort.lookupServiceDetail(namespace, serviceName);
        if (item == null) {
            // 厂商 `existService(clusterID, serviceName).isData()` 为 false 时抛的就是这个键
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.service.name.not.found", serviceName);
        }
        // 厂商 `:1476-1479`：先取 i18n 自动名，**空白**再回退注解 `gaop/serviceLabel`
        String label = item.getServiceLabel();
        String us = autoNameForLanguage("en_US", serviceName);
        item.setNameUs(us == null || us.isBlank() ? label : us);
        String fr = autoNameForLanguage("fr_FR", serviceName);
        item.setNameFr(fr == null || fr.isBlank() ? label : fr);
        // 厂商 `:1433-1435`：`servicesItem.setTerminated(...)` / `setHostIP(...)`
        // 厂商 `:1436-1437`：`imageInfo` = `deployment...containers.get(0).getImage()`（实体里的 image 就是它）
        return item;
    }

    /**
     * `i18n.service.<lang>.<key>` 的原值（**不做**语言回退、不按当前语言挑）——
     * 厂商详情端点写死了 `en_US` 与 `fr_FR` 两次（`:1476-1479`）。
     */
    private String autoNameForLanguage(String language, String serviceKey) {
        return i18nRepository.queryValue("i18n.service." + language + "." + serviceKey).orElse(null);
    }

    /**
     * 更新服务的战位标识（厂商 `updateServiceRole:1483-1531`，规格见
     * {@link IServiceQueryService#updateServiceRole}）。
     */
    @Override
    public boolean updateServiceRole(String namespace, String serviceKey, String name) {
        // 先读 Deployment；读不到回退 StatefulSet；两者都没有 ⇒ 400 `service.get.service.failed.not.found`
        var ref = serviceRuntimePort.readWorkloadRef(namespace, name);
        if (ref == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.get.service.failed.not.found");
        }
        boolean isDeployment = ref.isDeployment();

        // 厂商那段**缺陷**照抄：
        //   HashMap<String,String> annotations = new HashMap<>();
        //   annotations.put("gaop/serviceRole", serviceKey);
        //   if (isDeployment) { annotations = v1Deployment.getSpec()...getAnnotations();   // ← 覆盖掉了刚才的 put
        //                       v1Deployment.getSpec()...setAnnotations(annotations); }
        // 于是只有"工作负载的 annotations 原本为 null"时 `gaop/serviceRole` 才会被写进去。
        Map<String, String> workloadAnnotations =
                serviceRuntimePort.readWorkloadTemplateAnnotations(namespace, name);
        Map<String, String> annotations = new java.util.HashMap<>();
        annotations.put("gaop/serviceRole", serviceKey);
        if (workloadAnnotations != null) {
            annotations = workloadAnnotations;   // 照抄：刚才那个 put 被丢弃
        }

        try {
            // 厂商序列化的始终是 Deployment 对象；StatefulSet 分支下它是 null（body "null"）。
            // 我们按 isDeployment 走，失败再回退——分支顺序与厂商一致。
            if (isDeployment) {
                serviceRuntimePort.patchDeploymentTemplateMetadata(namespace, name, annotations);
            } else {
                serviceRuntimePort.patchStatefulSetTemplateMetadata(namespace, name, annotations);
            }
            return true;
        } catch (Exception e) {
            log.error("更新deployment失败", e);
            try {
                serviceRuntimePort.patchStatefulSetTemplateMetadata(namespace, name, annotations);
                return true;
            } catch (Exception statefulSetException) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.update.deployment.failed");
            }
        }
    }

    /**
     * 设置/取消软件的服务入口（厂商 `SoftwareService.setServiceEntry:2238-2284`，规格见
     * {@link IServiceQueryService#setServiceEntry}）。
     *
     * <p>**分层说明**：厂商这个方法在 `SoftwareService` 里；我们把它放在服务域的实现上，
     * 因为这里已经注入了 `IServiceRuntimePort` 与 `IGatewayRefreshPort`，而
     * `SoftwareCommandServiceImpl` 是 `@RequiredArgsConstructor`（新增构造参数会牵动 4 个测试的构造调用）。
     */
    @Override
    public void setServiceEntry(String namespace, String softwareId, String serviceName, Integer port, Integer type) {
        var software = softwareRepository.queryById(softwareId);
        if (software.isEmpty()) {
            log.warn("软件不存在: {}", softwareId);
            return;
        }
        // 厂商 `softwareAllService(softwareId)` 后 `anyMatch(e -> serviceName.equals(e.getAppName()))`
        List<String> appNames = softwareRepository.queryAppNamesBySoftwareId(softwareId);
        boolean contain = appNames != null && appNames.stream().anyMatch(serviceName::equals);
        if (!contain) {
            log.warn("软件没有绑定该服务：{}", serviceName);
            return;
        }
        if (!serviceRuntimePort.serviceExists(namespace, serviceName)) {
            log.warn("不存在该服务: {}", serviceName);
            return;
        }
        var snapshot = serviceRuntimePort.readServiceSnapshot(namespace, serviceName);
        if (snapshot == null || snapshot.isEmpty()) {
            log.warn("不存在该服务：{}", serviceName);
            return;
        }
        int targetPort = port == null ? 0 : port;
        if (type != null && type == 1) {
            // ① 该行 web_entry = true
            softwareRepository.updateWebEntry(softwareId, serviceName, targetPort, true);
            // ② 该软件下除该行以外全部 web_entry = 0
            softwareRepository.clearOtherWebEntries(softwareId, serviceName, targetPort);
            // ③ router_key = "/" + key + "/"
            softwareRepository.updateRouterKey(softwareId, "/" + software.get().getKey() + "/",
                    System.currentTimeMillis());
        }
        if (type != null && type == 0) {
            softwareRepository.updateWebEntry(softwareId, serviceName, targetPort, false);
            softwareRepository.updateRouterKey(softwareId, null, System.currentTimeMillis());
        }
        gatewayRefreshPort.refresh();
    }

    /**
     * 重置服务配置（厂商 `AppService.resetService:539-570`，规格见
     * {@link IServiceQueryService#resetService}）。**破坏性**。
     */
    @Override
    public boolean resetService(String namespace) {
        Map<String, Map<String, String>> deployments;
        try {
            deployments = serviceRuntimePort.listDeploymentLabels(namespace);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("重置服务出现异常:{}", e.getMessage(), e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.reset.failed");
        }
        for (var entry : deployments.entrySet()) {
            Map<String, String> labels = entry.getValue();
            // 厂商：带 `ingress-lb` 标签的跳过
            if (labels != null && labels.containsKey("ingress-lb")) {
                continue;
            }
            log.info("重置服务名称：{}", entry.getKey());
            log.info("delete deployment: {}", entry.getKey());
            try {
                serviceRuntimePort.deleteDeployment(namespace, entry.getKey());
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("重置服务出现异常:{}", e.getMessage(), e);
                throw new com.csm.paas.core.types.exception.WrongArgumentException("service.reset.failed");
            }
        }
        return true;
    }

    /**
     * 镜像存储大小（厂商 `K3sImageServiceImpl.imageStorageInfo:128-171` + `SshUtil.getImageStorage:521-561`，
     * 规格见 {@link IServiceQueryService#queryImageStorage}）。**类 C：不可在线验证**。
     */
    @Override
    public com.csm.paas.core.domain.software.model.valobj.StorageInfoVO queryImageStorage(String mountPath) {
        String command = " df -h " + mountPath + " | awk 'NR==2 {print $2, $4}'";
        String output;
        try {
            output = sshPort.exec(command);
        } catch (Exception e) {
            // 厂商：连接不上 / 命令异常 ⇒ `{free:0,total:0}`（不报错）
            log.error("请求ssh获镜像存储信息失败,异常为:", e);
            return com.csm.paas.core.domain.software.model.valobj.StorageInfoVO.builder()
                    .free(0L).total(0L).build();
        }
        if (output == null || output.isBlank()) {
            return com.csm.paas.core.domain.software.model.valobj.StorageInfoVO.builder()
                    .free(0L).total(0L).build();
        }
        String[] split = output.split("\\s+");
        // 照抄：厂商直接取 split[0]/split[1]，数组短了会 ArrayIndexOutOfBounds（这里主动收紧，见 deviations B25）
        if (split.length < 2) {
            return com.csm.paas.core.domain.software.model.valobj.StorageInfoVO.builder()
                    .free(0L).total(0L).build();
        }
        return com.csm.paas.core.domain.software.model.valobj.StorageInfoVO.builder()
                .total(switchSize(split[0]))
                .free(switchSize(split[1]))
                .build();
    }

    /**
     * 厂商 `SshUtil.switchSize:541-561` 的逐行照抄：**三个独立的 if**（不是 else-if），
     * 只认 `T`/`G`/`M` 后缀，其它（含没有后缀的纯字节数）一律得到 **0**。
     */
    private static long switchSize(String value) {
        long size = 0L;
        if (value.endsWith("T")) {
            size = (long) (Double.parseDouble(value.replaceAll("T", "")) * 1024.0 * 1024.0 * 1024.0 * 1024.0);
        }
        if (value.endsWith("G")) {
            size = (long) (Double.parseDouble(value.replaceAll("G", "")) * 1024.0 * 1024.0 * 1024.0);
        }
        if (value.endsWith("M")) {
            size = (long) (Double.parseDouble(value.replaceAll("M", "")) * 1024.0 * 1024.0);
        }
        return size;
    }

    /**
     * 服务历史日志（厂商 `TenxCloudImpl.getHistoryLog:159-235` 的前置校验 + 取原始 JSON，
     * 规格见 {@link IServiceQueryService#queryServiceHistoryLogJson}）。**类 C：不可在线验证**。
     */
    @Override
    public String queryServiceHistoryLogJson(String serviceName, java.util.Date startTime, java.util.Date endTime,
                                             Integer size, String keyword) {
        java.util.Date start = startTime == null ? new java.util.Date() : startTime;
        java.util.Date end = endTime == null ? new java.util.Date() : endTime;
        int limit = size == null || size < 0 ? 1000 : size;
        java.time.Duration between = java.time.Duration.between(start.toInstant(), end.toInstant());
        if (between.toHours() > 720L) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("query.time.range.must.between.720");
        }
        return lokiLogPort.queryRange(serviceName, keyword == null ? "" : keyword, limit,
                start.getTime(), end.getTime());
    }

    /**
     * 容器资源使用 Top5（厂商 `SoftwareService.topPodResource:1734-1873`，规格见
     * {@link IServiceQueryService#queryTopPodResources}）。**类 C：不可在线验证**。
     */
    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceInfoVO> queryTopPodResources(
            String type, String namespace) {
        String ns = namespace == null || namespace.isEmpty() ? this.defaultNamespace : namespace;
        String kind = type == null ? "" : type;
        List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO> podResources = switch (kind) {
            case "cpu" -> prometheusMetricPort.query(String.format(
                    "topk(5,avg(rate(container_cpu_usage_seconds_total"
                            + "{docker_type='container', namespace='%s'}[5m])) by (pod_name,pod_namespace))", ns));
            case "mem" -> prometheusMetricPort.query(String.format(
                    "topk(5, sum by (pod_name,pod_namespace) "
                            + "(container_memory_usage_bytes{pod_name!=\"\",namespace=\"%s\"}))", ns));
            case "netout" -> prometheusMetricPort.query(String.format(
                    "topk(5, sum by (pod_name,pod_namespace) "
                            + "(rate(container_network_transmit_bytes_total{pod_name!=\"\",namespace=\"%s\"}[5m])))", ns));
            case "netin" -> prometheusMetricPort.query(String.format(
                    "topk(5, sum by (pod_name,pod_namespace) "
                            + "(rate(container_network_receive_bytes_total{pod_name!=\"\",namespace=\"%s\"}[5m])))", ns));
            case "diskread" -> prometheusMetricPort.query(String.format(
                    "topk(5, sum by (pod_name,pod_namespace) "
                            + "(rate(container_fs_reads_bytes_total{pod_name!=\"\",namespace=\"%s\"}[5m])))", ns));
            case "diskwrite" -> prometheusMetricPort.query(String.format(
                    "topk(5, sum by (pod_name,pod_namespace) "
                            + "(rate(container_fs_writes_bytes_total{pod_name!=\"\",namespace=\"%s\"}[5m])))", ns));
            default -> List.of();
        };
        List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceInfoVO> list = new ArrayList<>();
        for (var r : podResources) {
            var item = com.csm.paas.core.domain.software.model.valobj.TopPodResourceInfoVO.builder()
                    .podName(r.getPodName())
                    .namespace(r.getNamespace())
                    .build();
            switch (kind) {
                case "cpu" -> item.setCpuUsageRate(r.getValue());
                case "mem" -> item.setMemory(r.getValue());
                case "netout" -> item.setNetworkOutBound(r.getValue());
                case "netin" -> item.setNetworkInBound(r.getValue());
                case "diskread" -> item.setDiskRead(r.getValue());
                case "diskwrite" -> item.setDiskWrite(r.getValue());
                default -> { }
            }
            list.add(item);
        }
        // 厂商随后对每个 pod 逐项补齐其它 5 项：**该字段为空才查**，查不到写 "0"
        // （`SoftwareService:1814-1872`：cpu 是**无条件**覆盖，其余 5 项都是 `if (s.getX() == null || isEmpty())`）
        for (var s : list) {
            Integer cpuNum = cpuNumOfPod(s.getPodName(), ns);
            s.setCpuUsageRate(firstValueOrZero(prometheusMetricPort.query(String.format(
                    "avg(rate(container_cpu_usage_seconds_total"
                            + "{pod_name=~\"^%s.*$\",docker_type='container', namespace='%s'}[5m])) "
                            + "by (pod_name,pod_namespace)*%d",
                    s.getPodName(), ns, cpuNum))));
            if (isEmptyString(s.getMemory())) {
                s.setMemory(firstValueOrZero(prometheusMetricPort.query(String.format(
                        "container_memory_usage_bytes{pod_name=~\"^%s.*$\",namespace=\"%s\"}",
                        s.getPodName(), ns))));
            }
            if (isEmptyString(s.getNetworkOutBound())) {
                // 照抄：netout 的正则**没有** `^`
                s.setNetworkOutBound(firstValueOrZero(prometheusMetricPort.query(String.format(
                        "rate(container_network_transmit_bytes_total{pod_name=~\"%s.*$\","
                                + "namespace=\"%s\"}[5m])",
                        s.getPodName(), ns))));
            }
            if (isEmptyString(s.getNetworkInBound())) {
                s.setNetworkInBound(firstValueOrZero(prometheusMetricPort.query(String.format(
                        "rate(container_network_receive_bytes_total{pod_name=~\"^%s.*$\","
                                + "namespace=\"%s\"}[5m])",
                        s.getPodName(), ns))));
            }
            if (isEmptyString(s.getDiskRead())) {
                s.setDiskRead(firstValueOrZero(prometheusMetricPort.query(String.format(
                        "rate(container_fs_reads_bytes_total{pod_name=~\"^%s.*$\","
                                + "namespace=\"%s\"}[5m])",
                        s.getPodName(), ns))));
            }
            if (isEmptyString(s.getDiskWrite())) {
                s.setDiskWrite(firstValueOrZero(prometheusMetricPort.query(String.format(
                        "rate(container_fs_writes_bytes_total{pod_name=~\"^%s.*$\","
                                + "namespace=\"%s\"}[5m])",
                        s.getPodName(), ns))));
            }
        }
        return list;
    }

    /** 厂商 `getResourceMetric` 的消费口径：**取第 0 条**，没有就写 `"0"`。 */
    private static String firstValueOrZero(List<com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(0).getValue();
        }
        return "0";
    }

    /**
     * 厂商那 5 个 `if (s.getX() == null || s.getX().isEmpty())` 的**精确**等价判断。
     * <p>刻意**不用** {@code String.isBlank()}（那个把纯空白也算空），也不用本类里已有的
     * `isBlank`（同样的 isBlank 语义）——厂商写的是 `isEmpty()`。
     */
    private static boolean isEmptyString(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 厂商 `getCpuNum:1876-1912`：读该 pod 的 `status.hostIP`，在节点列表里找 **InternalIP 相同**的节点，
     * 取 `status.allocatable.cpu` 的整数部分；读 pod / 读节点失败都只记日志并用 **1**。
     */
    private Integer cpuNumOfPod(String podName, String namespace) {
        Integer cpuNum = 1;
        String hostIp = null;
        try {
            hostIp = serviceRuntimePort.readPodHostIp(namespace, podName);
        } catch (RuntimeException e) {
            log.error("获取容器:{}所属节点ip失败,默认设置为1核,异常为:{}", podName, e.getMessage());
        }
        try {
            var cpu = serviceRuntimePort.readNodeAllocatableCpuByInternalIp(hostIp);
            if (cpu != null) {
                cpuNum = cpu.intValue();
            }
        } catch (RuntimeException e) {
            log.error("获取节点信息失败,默认设置为1核,异常为:{}", e.getMessage());
        }
        log.info("获取到容器:{}所属节点的cpu的核数为:{}", podName, cpuNum);
        return cpuNum;
    }

    /**
     * 某软件关联的服务列表（厂商 `softwareRelationServiceList:3064-3158`，规格见 `docs/findings/120`）。
     */
    @Override
    public List<ServiceRuntimeEntity> querySoftwareRelationServices(String namespace, String softwareId) {
        if (softwareRepository.queryById(softwareId).isEmpty()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "software.get.software.service.info.failed");
        }
        List<String> appNames = softwareRepository.queryAppNamesBySoftwareId(softwareId);
        if (appNames == null || appNames.isEmpty()) {
            return List.of();
        }
        Map<String, ServiceRuntimeEntity> byName = serviceRuntimePort.queryServices(namespace).stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(ServiceRuntimeEntity::getName, Function.identity(), (a, b) -> a));
        long now = System.currentTimeMillis();
        String language = languageQueryService.queryCurrent().getLanguage();
        List<ServiceRuntimeEntity> result = new ArrayList<>();
        for (String appName : appNames) {
            ServiceRuntimeEntity item = byName.get(appName);
            if (item == null) {
                // 厂商：`serviceMap.get(appName)` 不判空 ⇒ 紧接着 setMappingInfo **NPE ⇒ 500**。
                // 关联残留（服务删了、g_software_app 没清）在本环境很常见，整页 500 不可接受
                // ⇒ 跳过该行（deviations B17）。
                log.warn("关联服务 {} 在 k8s 里不存在，跳过（厂商此处会 NPE）", appName);
                continue;
            }
            item.setMappingInfo(buildMappingInfo(namespace, appName, item));
            applyServiceAuth(appName, item, now);
            applyAutoServiceLabel(item, appName, language);
            result.add(item);
        }
        return result;
    }

    /**
     * 厂商 `getServiceMappingInfo:2974-3048`：`intranetInfo`/`nodePortInfo` 由 Service 与
     * `<name>-nodeport` 的端口算出；`ingressInfo` 读的 `proxy` 恒 null ⇒ **恒空**。
     * <p>Service 对象缺失 ⇒ 400 `service.get.service.info.error`（厂商 `:2997-3000` 就是这么抛的）。
     */
    private com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO buildMappingInfo(
            String namespace, String serviceName, ServiceRuntimeEntity item) {
        if (item.getServiceSnapshot() == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.get.service.info.error");
        }
        List<com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.IntranetInfoVO> intranet = new ArrayList<>();
        List<com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.NodePortInfoVO> nodePorts = new ArrayList<>();
        for (Map<String, Object> port : servicePortsOf(item.getServiceSnapshot())) {
            Integer portNumber = intOf(port.get("port"));
            intranet.add(com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.IntranetInfoVO.builder()
                    .containerPort(portNumber)
                    .intranetPort(serviceName + ":" + portNumber)
                    .build());
            if (port.get("nodePort") == null) {
                continue;
            }
            nodePorts.add(com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.NodePortInfoVO.builder()
                    .containerPort(portNumber)
                    .nodePort(intOf(port.get("nodePort")))
                    .protocol((String) port.get("protocol"))
                    .build());
        }
        Map<String, Object> nodePortService = serviceRuntimePort.readServiceSnapshot(namespace, serviceName + "-nodeport");
        if (nodePortService != null) {
            for (Map<String, Object> port : servicePortsOf(nodePortService)) {
                nodePorts.add(com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.NodePortInfoVO.builder()
                        .containerPort(intOf(port.get("port")))
                        .nodePort(intOf(port.get("nodePort")))
                        .protocol((String) port.get("protocol"))
                        .build());
            }
        }
        return com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO.builder()
                .serviceName(serviceName)
                .intranetInfo(intranet)
                .nodePortInfo(nodePorts)
                // 厂商 `servicesItem.getService().getProxy()` 恒 null（全仓库无 setProxy）⇒ 恒空
                .ingressInfo(List.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> servicePortsOf(Map<String, Object> serviceSnapshot) {
        Object spec = serviceSnapshot.get("spec");
        if (!(spec instanceof Map<?, ?> specMap)) {
            return List.of();
        }
        Object ports = ((Map<String, Object>) specMap).get("ports");
        return ports instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static Integer intOf(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 厂商 `:3124-3143`：没有授权行且 `gaop/serviceType == "前台服务"` ⇒ 造一个 `{auth:1, authType:1, serviceId}`；
     * 有行且 `now > authTime` ⇒ 改成 `EXPIRED(2)` 并**写库**（读接口带副作用，照抄）。
     */
    private void applyServiceAuth(String serviceName, ServiceRuntimeEntity item, long now) {
        var existing = serviceAuthRepository.queryByServiceId(serviceName);
        if (existing.isEmpty()) {
            if ("前台服务".equals(item.getServiceType())) {
                item.setServiceAuth(com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity.builder()
                        .serviceId(serviceName)
                        .auth(AUTH_AUTHORIZED)
                        .authType(1)
                        .build());
            } else {
                item.setServiceAuth(null);
            }
            return;
        }
        var auth = existing.get();
        if (auth.getAuthTime() != null && now > auth.getAuthTime()) {
            auth.setAuth(AUTH_EXPIRED);
            serviceAuthRepository.updateAuth(serviceName, AUTH_EXPIRED);
        }
        item.setServiceAuth(auth);
    }

    /**
     * 厂商 `:3144-3147`：把 `deployment` 快照里注解 `gaop/serviceLabel` 换成当前语言的自动名
     * （`getAutoNameMessageByCurrent("service", key, name)`——**只有 en_US/fr_FR 才查覆盖表**，其它语言返回原值）。
     * <p>注意厂商改的是**响应里的快照对象**，不写集群。
     */
    @SuppressWarnings("unchecked")
    private void applyAutoServiceLabel(ServiceRuntimeEntity item, String serviceName, String language) {
        Map<String, Object> deployment = item.getDeploymentSnapshot();
        if (deployment == null) {
            return;
        }
        Object spec = deployment.get("spec");
        if (!(spec instanceof Map<?, ?> specMap)) {
            return;
        }
        Object template = ((Map<String, Object>) specMap).get("template");
        if (!(template instanceof Map<?, ?> templateMap)) {
            return;
        }
        Object metadata = ((Map<String, Object>) templateMap).get("metadata");
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            return;
        }
        Object annotations = ((Map<String, Object>) metadataMap).get("annotations");
        if (!(annotations instanceof Map<?, ?> annotationMap)) {
            return;
        }
        Map<String, Object> mutable = (Map<String, Object>) annotationMap;
        String current = (String) mutable.get("gaop/serviceLabel");
        String auto = autoName(serviceName, language, current);
        if (auto != null) {
            mutable.put("gaop/serviceLabel", auto);
        }
    }

    /** 厂商 `I18nUtil.getAutoNameMessageByCurrent:130-142`：只对 en_US / fr_FR 查 `g_setting`。 */
    private String autoName(String serviceName, String language, String fallback) {
        if (!"en_US".equals(language) && !"fr_FR".equals(language)) {
            return fallback;
        }
        return i18nRepository.queryValue("i18n.service." + language + "." + serviceName)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    /**
     * 基础服务的 Event 列表（厂商 `getBaseServiceEvent:3377-3421`，规格见 `docs/findings/125`）。
     */
    @Override
    public List<Map<String, Object>> queryBaseServiceEvents(String baseServiceName) {
        // 厂商：只有 kube-proxy 用 `k8s-app` 选择器，其它基础服务用 `name`
        String labelKey = "kube-proxy".equals(baseServiceName) ? "k8s-app" : "name";
        List<com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO> pods;
        try {
            pods = serviceRuntimePort.listPodsByLabel(KUBE_SYSTEM_NAMESPACE, labelKey, baseServiceName);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取基础服务容器信息异常:{}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.get.basic.service.container.info.failed");
        }
        List<String> podNames = pods.stream()
                .map(com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO::getName)
                .toList();
        // 厂商：ReplicaSet 名**直接**用 `<baseServiceName>-`（与 /event 从 generateName 推不同）
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            events.addAll(serviceRuntimePort.listEvents(KUBE_SYSTEM_NAMESPACE, baseServiceName + "-", "ReplicaSet"));
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("事件异常:{}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.get.container.event.info.failed");
        }
        for (String podName : podNames) {
            try {
                events.addAll(serviceRuntimePort.listEvents(KUBE_SYSTEM_NAMESPACE, podName, "Pod"));
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("pod事件异常:{}", e.getMessage());
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.get.container.pod.event.info.failed");
            }
        }
        // 厂商：`Comparator.comparing(lastTimestamp).reversed()` —— **没有空值保护** ⇒ 缺 lastTimestamp 时 NPE ⇒ 500。
        // 我们按"时间降序、空值排最后"处理（deviations B19）。
        events.sort((a, b) -> {
            var ta = eventTimestamp(a);
            var tb = eventTimestamp(b);
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        return events;
    }

    /**
     * kube-system 基础组件列表（厂商 `getKubeSystemInfo:3218-3311`，规格见 `docs/findings/128`）。
     */
    @Override
    public List<Map<String, Object>> queryBaseComponents(String nodeName) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(baseComponent("weave-net", "name", nodeName));
        result.add(baseComponent("kube-proxy", "k8s-app", nodeName));
        // 厂商：keepalived 的 pod 列表为空（或读取失败）⇒ **整个条目不出现**
        Map<String, Object> keepAlived = baseComponent("kube-keepalived-vip", "name", nodeName);
        if (keepAlived.get("v1PodList") != null) {
            result.add(keepAlived);
        }
        return result;
    }

    /**
     * 一个基础组件的两个键：`daemonSet` 与 `v1PodList`。
     * <p>厂商对这两种读的异常**都只记日志**（组件条目照常出现，字段为 null）。
     */
    private Map<String, Object> baseComponent(String daemonSetName, String labelKey, String nodeName) {
        Map<String, Object> row = new java.util.HashMap<>();
        Object daemonSet = null;
        try {
            daemonSet = serviceRuntimePort.readDaemonSetSnapshot(KUBE_SYSTEM_NAMESPACE, daemonSetName);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取{}组件服务出现异常，异常为:{}", daemonSetName, e.getMessage());
        }
        List<Map<String, Object>> pods = null;
        try {
            List<Map<String, Object>> found = serviceRuntimePort.listPodsByLabelAndNode(
                    KUBE_SYSTEM_NAMESPACE, labelKey, daemonSetName, nodeName);
            // 厂商：**只有非空才 set**（空列表时字段保持 null）
            pods = found.isEmpty() ? null : found;
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取{}下所属节点:{}下的pod容器出现异常，异常为:{}", daemonSetName, nodeName, e.getMessage());
        }
        row.put("daemonSet", daemonSet);
        row.put("v1PodList", pods);
        return row;
    }

    /**
     * 批量重启（厂商 `serviceRestart:3826-3912`，规格见 `docs/findings/127`）。
     */
    @Override
    public boolean batchRestartServices(String namespace, List<String> serviceNames) {
        if (serviceNames == null || serviceNames.isEmpty()) {
            return true;
        }
        for (String serviceName : serviceNames) {
            var state = serviceRuntimePort.readWorkloadState(namespace, serviceName);
            if (state == null) {
                log.info("未找到服务:{}跳过该服务重启", serviceName);
                continue;
            }
            if (!state.isDeployment()) {
                // StatefulSet 分支：只打注解 + replace，**不写 g_pod_event**
                restartWorkload(namespace, serviceName, false);
                continue;
            }
            String podName = firstPodNameByServiceLabel(namespace, serviceName);
            if (podName != null) {
                // 厂商：`restartNum` 初值 1，再取历史最大值（两处 set(1) 的写法照抄成 max(1, …)）
                int restartNum = 1;
                for (Integer count : softwareRepository.queryPodEventRestartCounts(serviceName, podName)) {
                    if (count != null && count > restartNum) {
                        restartNum = count;
                    }
                }
                softwareRepository.insertPodEvent(serviceName, podName, System.currentTimeMillis(), restartNum);
                log.info("将容器重启信息入库成功");
            }
            restartWorkload(namespace, serviceName, true);
        }
        return true;
    }

    /** 厂商：给 pod 模板打 `kubectl.kubernetes.io/restartedAt`（值是 `LocalDateTime.now().toString()`）后 replace。 */
    private void restartWorkload(String namespace, String serviceName, boolean deployment) {
        try {
            serviceRuntimePort.replaceWorkloadRestartedAt(namespace, serviceName, deployment,
                    java.time.LocalDateTime.now().toString());
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.info("重启服务:{}失败,异常为:{}", serviceName, e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.restart.name.failed", serviceName);
        }
    }

    /** 厂商 `getPodNameByDeploymentName`：按标签 `system/svcName` 找**第一个** pod；找不到返回 null。 */
    private String firstPodNameByServiceLabel(String namespace, String serviceName) {
        List<com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO> pods;
        try {
            pods = serviceRuntimePort.listPodsByLabel(namespace, "system/svcName", serviceName);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("出现异常", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.get.service.info.exception");
        }
        return pods.stream()
                .map(com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO::getName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 批量停止（厂商 `serviceStop:3649-3685`，规格见 `docs/findings/126`）。
     */
    @Override
    public boolean batchStopServices(String namespace, List<String> serviceNames) {
        if (serviceNames == null || serviceNames.isEmpty()) {
            return true;
        }
        for (String serviceName : serviceNames) {
            var state = serviceRuntimePort.readWorkloadState(namespace, serviceName);
            if (state == null) {
                log.info("找不到服务名称:{},跳过停止服务", serviceName);
                continue;
            }
            // 厂商：`Integer replicas = ...getReplicas(); if (replicas == 0)` —— null 会拆箱 NPE（不可达，见 B20）
            if (Integer.valueOf(0).equals(state.getSpecReplicas())) {
                log.info("服务名称:{}已经停止，跳过停止操作", serviceName);
                continue;
            }
            scaleWorkload(namespace, serviceName, state.isDeployment(), 0, "停止");
        }
        return true;
    }

    /**
     * 批量启动（厂商 `serviceStart:3689-3740`，规格见 `docs/findings/126`）。
     */
    @Override
    public boolean batchStartServices(String namespace, List<String> serviceNames) {
        if (serviceNames == null || serviceNames.isEmpty()) {
            return true;
        }
        for (String serviceName : serviceNames) {
            var state = serviceRuntimePort.readWorkloadState(namespace, serviceName);
            if (state == null) {
                log.info("找不到服务名称:{},跳过启动服务", serviceName);
                continue;
            }
            String status = serviceStatusOf(state);
            if ("STOP".equals(status)) {
                scaleWorkload(namespace, serviceName, state.isDeployment(), 1, "启动");
                continue;
            }
            if ("RUNNING".equals(status)) {
                log.info("服务名称:{}处于运行中，不进行启动操作", serviceName);
                continue;
            }
            if (" ROLLING_UPDATE".equals(status)) {
                log.info("服务名称:{}处于滚动中，不进行启动操作", serviceName);
                continue;
            }
            if ("STARING".equals(status)) {
                log.info("服务名称:{}处于启动中，不进行启动操作", serviceName);
            }
        }
        return true;
    }

    private void scaleWorkload(String namespace, String serviceName, boolean deployment, int replicas,
                               String operation) {
        try {
            serviceRuntimePort.replaceWorkloadReplicas(namespace, serviceName, deployment, replicas);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.info("{}服务:{}时出现异常,异常为:{}", operation, serviceName, e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.operation.failed", operation);
        }
    }

    /**
     * 厂商 `getServiceStatusByServicesItem` 的**两个重载**（`:3742-3792`）：字段集一样，
     * 但 Deployment 那份多要求 `unavailableReplicas != null`（StatefulSet 根本没有这个字段）。
     * <p>返回串照抄：`"STOP"` / `"RUNNING"` / `" ROLLING_UPDATE"`（**前导空格**）/ `"STARING"`（厂商拼写）。
     */
    private static String serviceStatusOf(com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO s) {
        return com.csm.paas.core.domain.software.model.valobj.WorkloadStatus.of(s);
    }

    /**
     * 软件总览（厂商 `getOverViewInfo:1631-1720`，规格见 `docs/findings/129`）。
     */
    @Override
    public com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity querySoftwareOverview(
            String namespace) {
        var result = com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity.builder()
                .software(newSoftwareStatus())
                .service(newSoftwareStatus())
                .pod(newSoftwareStatus())
                .build();
        List<ServiceRuntimeEntity> services = serviceRuntimePort.queryServices(namespace);
        Map<String, ServiceRuntimeEntity> byName = services.stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(ServiceRuntimeEntity::getName, Function.identity(), (a, b) -> a));

        // ---- software：遍历全部软件，每行按"关联服务的状态"汇总 ----
        for (var software : softwareRepository.queryAllSoftware()) {
            List<String> appNames = softwareRepository.queryAppNamesBySoftwareId(software.getId());
            List<String> statuses = new ArrayList<>();
            for (String appName : appNames) {
                ServiceRuntimeEntity item = byName.get(appName);
                // 厂商：找不到就按 "STOP" 算（不是跳过）
                statuses.add(item == null ? "STOP" : serviceStatusOf(stateOf(item)));
            }
            String softwareStatus = softwareStatusOf(statuses);
            if ("STOP".equals(softwareStatus)) {
                result.getSoftware().setStop(result.getSoftware().getStop() + 1);
            } else if ("RUNNING".equals(softwareStatus)) {
                result.getSoftware().setRunning(result.getSoftware().getRunning() + 1);
            } else {
                // PART_RUN（含空列表）⇒ pending
                result.getSoftware().setPending(result.getSoftware().getPending() + 1);
            }
        }

        // ---- service：遍历 k8s 服务列表，按标签过滤 ----
        for (ServiceRuntimeEntity item : services) {
            if (item.getDeploymentSnapshot() == null) {
                continue;
            }
            String appType = item.getAppType();
            if (appType != null) {
                if (appType.startsWith("gaop")) {
                    continue;
                }
            } else {
                String appName = item.getAppName();
                if ("gis-server".equals(appName) || "gis-jb".equals(appName) || "gaop".equals(appName)) {
                    continue;
                }
            }
            String status = serviceStatusOf(stateOf(item));
            if ("STOP".equals(status)) {
                result.getService().setStop(result.getService().getStop() + 1);
            } else if ("RUNNING".equals(status)) {
                result.getService().setRunning(result.getService().getRunning() + 1);
            } else {
                result.getService().setPending(result.getService().getPending() + 1);
            }
        }

        // ---- pod：遍历全部 pod，按 system/appName 过滤；列 pod 失败只记日志 ----
        try {
            for (Map<String, Object> pod : serviceRuntimePort.listAllPods(namespace)) {
                String appName = labelOfPod(pod, "system/appName");
                if ("gaop".equals(appName)) {
                    continue;
                }
                String phase = podPhaseOf(pod);
                if ("Pending".equals(phase) || "Terminating".equals(phase)) {
                    result.getPod().setPending(result.getPod().getPending() + 1);
                } else if ("Running".equals(phase)) {
                    result.getPod().setRunning(result.getPod().getRunning() + 1);
                } else {
                    result.getPod().setStop(result.getPod().getStop() + 1);
                }
            }
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取pod容器列表失败,异常为:{}", e.getMessage());
        }
        return result;
    }

    private static com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity.StatusInfo
    newSoftwareStatus() {
        return com.csm.paas.core.domain.software.model.entity.SoftwareOverviewEntity.StatusInfo.builder()
                .running(0).stop(0).pending(0).build();
    }

    /** 厂商 `getSoftwareStatus:2073-2089`：空列表 ⇒ STOP；RUNNING 计数为 0 ⇒ STOP；等于总数 ⇒ RUNNING；否则 PART_RUN。 */
    private static String softwareStatusOf(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return "STOP";
        }
        long countRun = statuses.stream().filter("RUNNING"::equals).count();
        if (countRun == 0) {
            return "STOP";
        }
        if (countRun == statuses.size()) {
            return "RUNNING";
        }
        return "PART_RUN";
    }

    /** 把工作负载快照里的 `status` 读成状态推导需要的字段（厂商直接读 `deployment.getStatus()`）。 */
    @SuppressWarnings("unchecked")
    private static com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO stateOf(
            ServiceRuntimeEntity item) {
        Map<String, Object> status = null;
        Object raw = item.getDeploymentSnapshot() == null ? null : item.getDeploymentSnapshot().get("status");
        if (raw instanceof Map<?, ?> map) {
            status = (Map<String, Object>) map;
        }
        return com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO.builder()
                .deployment(true)
                .replicas(statusInt(status, "replicas"))
                .readyReplicas(statusInt(status, "readyReplicas"))
                .availableReplicas(statusInt(status, "availableReplicas"))
                .updatedReplicas(statusInt(status, "updatedReplicas"))
                .unavailableReplicas(statusInt(status, "unavailableReplicas"))
                .build();
    }

    private static Integer statusInt(Map<String, Object> status, String key) {
        if (status == null) {
            return null;
        }
        Object value = status.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    /** pod 快照的 `metadata.labels[<key>]`。 */
    @SuppressWarnings("unchecked")
    private static String labelOfPod(Map<String, Object> pod, String key) {
        Object metadata = pod.get("metadata");
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            return null;
        }
        Object labels = ((Map<String, Object>) metadataMap).get("labels");
        return labels instanceof Map<?, ?> labelMap ? (String) ((Map<String, Object>) labelMap).get(key) : null;
    }

    /** pod 快照的 `status.phase`。 */
    @SuppressWarnings("unchecked")
    private static String podPhaseOf(Map<String, Object> pod) {
        Object status = pod.get("status");
        return status instanceof Map<?, ?> statusMap ? (String) ((Map<String, Object>) statusMap).get("phase") : null;
    }

    /**
     * 检测软件/服务是否有 ConfigMap 配置组（厂商 `existConfigMapBySoftwareOrService:3834-3913`，规格见 `docs/findings/133`）。
     */
    @Override
    public boolean existConfigMap(String namespace, String type, List<String> keys) {
        // 厂商：`key != null || key.size() != 0` ⇒ key 缺失时 NPE（且使"检查全部软件"分支永不可达）。
        // 我们按"没有 key ⇒ false"处理（deviations B22）。
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        if ("software".equals(type)) {
            for (String key : keys) {
                java.util.Set<String> names = new java.util.LinkedHashSet<>();
                softwareRepository.queryById(key).ifPresent(software -> {
                    for (String appName : softwareRepository.queryAppNamesBySoftwareId(software.getId())) {
                        names.addAll(serviceRuntimePort.readDeploymentConfigMapNames(namespace, appName));
                    }
                });
                if (!names.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        if ("service".equals(type)) {
            for (String key : keys) {
                if (!serviceRuntimePort.readDeploymentConfigMapNames(namespace, key).isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * 导出配置组（厂商 `exportConfigMapBySoftwareOrService:3915-4018`，规格见 `docs/findings/134`）。
     */
    @Override
    public List<Map<String, Object>> collectConfigMapsForExport(String namespace, String type, List<String> keys) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if ("software".equals(type)) {
            for (String key : keys == null ? List.<String>of() : keys) {
                softwareRepository.queryById(key).ifPresent(software -> {
                    for (String appName : softwareRepository.queryAppNamesBySoftwareId(software.getId())) {
                        names.addAll(serviceRuntimePort.readDeploymentConfigMapNames(namespace, appName));
                    }
                });
            }
        } else if ("service".equals(type)) {
            for (String key : keys == null ? List.<String>of() : keys) {
                try {
                    names.addAll(serviceRuntimePort.readDeploymentConfigMapNamesOrThrow(namespace, key));
                } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                    log.error("获取服务deployment出现异常,异常为:{}", e.getMessage());
                    throw new com.csm.paas.core.types.exception.WrongArgumentException(
                            "service.get.service.config.identifier.exception", key);
                }
            }
        }
        return serviceRuntimePort.readConfigMaps(namespace, new ArrayList<>(names));
    }

    /**
     * 更新软件配置里的服务端口（厂商 `softwareServicePort:2836-2876`，规格见 `docs/findings/136`）。
     */
    @Override
    public boolean updateSoftwareServicePorts(List<com.csm.paas.core.domain.software.model.valobj.ServicePortItemVO> form) {
        long currentTimeMillis = System.currentTimeMillis();
        List<Integer> portList = form.stream()
                .map(com.csm.paas.core.domain.software.model.valobj.ServicePortItemVO::getContainerPort)
                .toList();
        // 厂商：`form.get(0)` ⇒ 空列表 IndexOutOfBounds（500）；我们照抄不额外兜
        String serviceName = form.get(0).getServiceName();
        log.info("服务名称为:{}", serviceName);
        List<String> softwareIdList = softwareRepository.querySoftwareIdsByAppName(serviceName);
        if (softwareIdList.isEmpty()) {
            return true;
        }
        for (String softwareId : softwareIdList) {
            // 旧的 web 入口那一行：只有它的 port 还在新列表里，才把新行继续标成 web_entry=true
            Integer entryPort = softwareRepository.queryWebEntryPort(softwareId, serviceName).orElse(null);
            boolean isEntry = entryPort != null && portList.contains(entryPort);
            softwareRepository.deleteServicePorts(softwareId, serviceName);
            for (Integer port : portList) {
                softwareRepository.insertServicePortRow(softwareId, serviceName, port,
                        serviceName + "-" + port, isEntry && port.equals(entryPort), currentTimeMillis);
            }
        }
        gatewayRefreshPort.refresh();
        return true;
    }

    /**
     * 已被占用的 NodePort（厂商 `getNodePortUsedList:2120-2240`，规格见 `docs/findings/141`）。
     */
    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO> queryNodePortUsed(
            String namespace, Integer port) {
        List<com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO> result = new ArrayList<>();
        List<String> ingressNameList = new ArrayList<>();
        try {
            ingressNameList = serviceRuntimePort.listIngressLbValues(namespace);
            for (String ingressName : ingressNameList) {
                Map<String, String> annotations = serviceRuntimePort.readDeploymentAnnotations(namespace, ingressName);
                if (annotations.isEmpty()) {
                    continue;
                }
                // 厂商直接 Integer.valueOf(注解值)：缺失/非数字会抛（照抄，不额外兜）
                Integer httpPort = Integer.valueOf(annotations.get("httpPort"));
                result.add(com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO.builder()
                        .name(FRAMEWORK_INTERNAL).port(httpPort).build());
                Integer httpsPort = Integer.valueOf(annotations.get("httpsPort"));
                result.add(com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO.builder()
                        .name(FRAMEWORK_INTERNAL).port(httpsPort).build());
            }
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("ingress列表下暴露的tcp,udp相关端口失败，异常为:{}", e.getMessage());
        }
        for (String ingressName : ingressNameList) {
            String[] parts = ingressName.split("-");
            String endName = parts[1];   // 厂商：取第 2 段（没有 `-` 会 IndexOutOfBounds，照抄）
            for (String proto : List.of("tcp", "udp")) {
                Map<String, String> data = serviceRuntimePort.readConfigMapData(namespace,
                        proto + "-services-" + endName);
                if (data == null) {
                    continue;
                }
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    String serviceName = entry.getValue().split("/")[1];
                    result.add(com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO.builder()
                            .port(Integer.parseInt(entry.getKey())).name(serviceName).build());
                }
            }
        }
        try {
            for (Map<String, Object> svc : serviceRuntimePort.listNodePortServices(namespace)) {
                Map<String, Object> metadata = asMap(svc.get("metadata"));
                Map<String, Object> spec = asMap(svc.get("spec"));
                String name = metadata == null ? null : (String) metadata.get("name");
                Map<String, Object> labels = metadata == null ? null : asMap(metadata.get("labels"));
                if (labels != null) {
                    // 厂商：name = labels["name"]；为空再取 labels["app"]
                    String labelName = (String) labels.get("name");
                    name = (labelName == null || labelName.isBlank()) ? (String) labels.get("app") : labelName;
                }
                List<Object> ports = spec == null ? null : (List<Object>) spec.get("ports");
                for (Object rawPort : ports == null ? List.<Object>of() : ports) {
                    Map<String, Object> p = asMap(rawPort);
                    if (p == null) {
                        continue;
                    }
                    // 厂商：条目名是 `<name>:<service port>`，而 port 字段是 nodePort
                    result.add(com.csm.paas.core.domain.software.model.valobj.NodePortUsedVO.builder()
                            .name(name + ":" + p.get("port"))
                            .port(p.get("nodePort") instanceof Number n ? n.intValue() : null)
                            .build());
                }
            }
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取nodeport服务列表出错，异常为:{}", e.getMessage());
            return result;   // 厂商：直接返回（前两段结果保留）
        }
        if (port != null) {
            return result.stream().filter(d -> port.equals(d.getPort())).toList();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /**
     * 配置组列表（厂商 `listConfigMap:185-243`，规格见 `docs/findings/142`）。
     */
    @Override
    public com.csm.paas.core.domain.software.model.entity.ConfigMapListEntity queryConfigMapList(
            String namespace, int page, int size, String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.replaceAll(" +", "");
        Map<String, Object> listSnapshot;
        try {
            listSnapshot = serviceRuntimePort.listConfigMapsWithoutIngressLb(namespace);
        } catch (Exception e) {
            log.error("configmap列表查询失败异常", e);
            throw new com.csm.paas.core.types.exception.SystemException("configmap.get.list.failed");
        }
        List<Map<String, Object>> all = itemsOf(listSnapshot);
        // 厂商硬编码排除这两个名字
        List<Map<String, Object>> filtered = all.stream()
                .filter(v -> !"ingress-nginx-controller".equals(configMapName(v)))
                .filter(v -> !"kube-root-ca.crt".equals(configMapName(v)))
                .toList();
        long total = filtered.size();
        List<Map<String, Object>> pageItems;
        if (normalizedKeyword == null) {
            pageItems = slice(filtered, page, size);
        } else {
            List<Map<String, Object>> matched = filtered.stream()
                    .filter(v -> configMapName(v) != null
                            && configMapName(v).matches("(.*)" + normalizedKeyword + "(.*)"))
                    .toList();
            // 厂商：匹配为空 ⇒ items 置 null
            pageItems = matched.isEmpty() ? null : slice(matched, page, size);
        }
        return com.csm.paas.core.domain.software.model.entity.ConfigMapListEntity.builder()
                .apiVersion((String) listSnapshot.get("apiVersion"))
                .kind((String) listSnapshot.get("kind"))
                .metadata(asMap(listSnapshot.get("metadata")))
                .items(pageItems)
                .total(total)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemsOf(Map<String, Object> listSnapshot) {
        Object items = listSnapshot == null ? null : listSnapshot.get("items");
        return items instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String configMapName(Map<String, Object> configMap) {
        Map<String, Object> metadata = asMap(configMap.get("metadata"));
        return metadata == null ? null : (String) metadata.get("name");
    }

    /** 厂商 `skip((page-1)*size).limit(size)`（越界自然为空）。 */
    private static List<Map<String, Object>> slice(List<Map<String, Object>> source, int page, int size) {
        long from = (long) (page - 1) * size;
        return source.stream().skip(Math.max(from, 0)).limit(size).toList();
    }

    /** 厂商 `ConfigMapController.REGEX_CONFIGMAP_NAME`。 */
    private static final String REGEX_CONFIGMAP_NAME = "[-._a-zA-Z0-9]+";

    @Override
    public boolean configMapExists(String namespace, String name) {
        try {
            return serviceRuntimePort.findConfigMap(namespace, name).isPresent();
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("查询异常", e);
            throw new com.csm.paas.core.types.exception.SystemException("configmap.system.error");
        }
    }

    @Override
    public Map<String, Object> queryConfigMap(String namespace, String name) {
        if (name == null || name.isBlank()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("configmap.name.must.not.empty");
        }
        if (!name.matches(REGEX_CONFIGMAP_NAME)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("configmap.name.not.invalid");
        }
        java.util.Optional<Map<String, Object>> found;
        try {
            found = serviceRuntimePort.findConfigMap(namespace, name);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("配置组信息接口错误: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.SystemException("configmap.find.configmap.api.failed");
        }
        return found.orElseThrow(() -> new com.csm.paas.core.types.exception.WrongArgumentException(
                "configmap.not.exist", name));
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity> queryConfigMapRelations(
            String namespace, String name) {
        java.util.Optional<Map<String, Object>> found;
        try {
            found = serviceRuntimePort.findConfigMap(namespace, name);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取configmap失败，异常为:{}", e.getMessage());
            return List.of();
        }
        if (found.isEmpty()) {
            return List.of();
        }
        // data 的 key ⇒ subPath 名（初始空列表）
        Map<String, List<com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity.RelationServiceEntity>>
                subPathToServices = new java.util.LinkedHashMap<>();
        Map<String, Object> data = asMap(found.get().get("data"));
        if (data != null) {
            for (String key : data.keySet()) {
                subPathToServices.put(key, new ArrayList<>());
            }
        }
        for (Map<String, Object> deployment : serviceRuntimePort.listDeploymentSnapshots(namespace)) {
            String deploymentName = configMapName(deployment);
            Map<String, Object> podTemplate = asMap(asMap(deployment.get("spec")).get("template"));
            Map<String, Object> podSpec = podTemplate == null ? null : asMap(podTemplate.get("spec"));
            List<Object> volumes = podSpec == null ? null : (List<Object>) podSpec.get("volumes");
            if (volumes == null) {
                continue;
            }
            for (Object rawVolume : volumes) {
                Map<String, Object> volume = asMap(rawVolume);
                Map<String, Object> configMap = volume == null ? null : asMap(volume.get("configMap"));
                if (configMap == null || !name.equals(configMap.get("name"))) {
                    continue;
                }
                List<Object> containers = podSpec.get("containers") == null ? null
                        : (List<Object>) podSpec.get("containers");
                for (Object rawContainer : containers == null ? List.<Object>of() : containers) {
                    List<Object> mounts = asMap(rawContainer) == null ? null
                            : (List<Object>) asMap(rawContainer).get("volumeMounts");
                    for (Object rawMount : mounts == null ? List.<Object>of() : mounts) {
                        Map<String, Object> mount = asMap(rawMount);
                        if (mount == null || !volume.get("name").equals(mount.get("name"))) {
                            continue;
                        }
                        Object subPath = mount.get("subPath");
                        if (subPath == null || !subPathToServices.containsKey(subPath)) {
                            continue;
                        }
                        subPathToServices.get(subPath).add(
                                com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity
                                        .RelationServiceEntity.builder()
                                        .serviceName(deploymentName)
                                        .mountPath((String) mount.get("mountPath"))
                                        .build());
                    }
                }
            }
        }
        return subPathToServices.entrySet().stream()
                .map(e -> com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity.builder()
                        .configMapName(e.getKey()).config(e.getValue()).build())
                .sorted(java.util.Comparator.comparing(
                        com.csm.paas.core.domain.software.model.entity.ConfigMapInfoEntity::getConfigMapName))
                .toList();
    }

    @Override
    public Map<String, Object> createConfigMap(String namespace, String name) {
        validateConfigMapName(name);
        if (configMapExists(namespace, name)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("configmap.existed", name);
        }
        try {
            return serviceRuntimePort.createConfigMap(namespace, name);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("创建configmap失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.SystemException("configmap.create.failed");
        }
    }

    @Override
    public Map<String, Object> upsertConfigEntry(String namespace, String configMapName,
                                                 String entryName, String value) {
        validateConfigMapName(entryName);
        Map<String, Object> configMap = readConfigMapOrThrow(namespace, configMapName);
        Map<String, String> data = new java.util.LinkedHashMap<>();
        Map<String, Object> existing = asMap(configMap.get("data"));
        if (existing != null) {
            existing.forEach((k, v) -> data.put(k, v == null ? null : String.valueOf(v)));
        }
        // 厂商：value 为空（isEmpty）⇒ 存空串
        data.put(entryName, value == null || value.isEmpty() ? "" : value);
        return replaceOrThrow(namespace, configMapName, data);
    }

    @Override
    public Map<String, Object> deleteConfigEntry(String namespace, String configMapName, String entryName) {
        Map<String, Object> configMap = readConfigMapOrThrow(namespace, configMapName);
        Map<String, Object> existing = asMap(configMap.get("data"));
        if (existing == null || !existing.containsKey(entryName)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "configmap.name.not.exist", entryName);
        }
        Map<String, String> data = new java.util.LinkedHashMap<>();
        existing.forEach((k, v) -> {
            if (!k.equals(entryName)) {
                data.put(k, v == null ? null : String.valueOf(v));
            }
        });
        return replaceOrThrow(namespace, configMapName, data);
    }

    @Override
    public List<String> deleteConfigMaps(String namespace, String names) {
        List<String> deleted = new ArrayList<>();
        for (String raw : names.split(",")) {
            try {
                if (serviceRuntimePort.deleteConfigMap(namespace, raw.trim())) {
                    // 厂商：加入的是**未 trim 的原值**
                    deleted.add(raw);
                }
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("configmap删除失败: {}", e.getMessage());
            }
        }
        return deleted;
    }

    /** 厂商 `createConfig`/`deleteConfig` 共用的前置校验（名字空白/非法）。 */
    private void validateConfigMapName(String name) {
        if (name == null || name.isBlank()) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("configmap.name.must.not.empty");
        }
        if (!name.matches(REGEX_CONFIGMAP_NAME)) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("configmap.name.not.invalid");
        }
    }

    /** 读 ConfigMap（不区分 404）；读不到 ⇒ 400 `configmap.not.exist`；其它错误 ⇒ 500 `configmap.get.config.error`。 */
    private Map<String, Object> readConfigMapOrThrow(String namespace, String name) {
        java.util.Optional<Map<String, Object>> found;
        try {
            found = serviceRuntimePort.findConfigMap(namespace, name);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取配置组失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.SystemException("configmap.get.config.error", name);
        }
        return found.orElseThrow(() -> new com.csm.paas.core.types.exception.WrongArgumentException(
                "configmap.not.exist", name));
    }

    private Map<String, Object> replaceOrThrow(String namespace, String name, Map<String, String> data) {
        try {
            return serviceRuntimePort.replaceConfigMapData(namespace, name, data);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("创建配置接口调用失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.SystemException("configmap.request.api.error");
        }
    }

    @Override
    public List<Map<String, Object>> exportConfigMaps(String namespace, List<String> nameList) {
        if (nameList != null && !nameList.isEmpty()) {
            // 厂商：按名读，读不到的跳过
            return serviceRuntimePort.readConfigMaps(namespace, nameList);
        }
        // 厂商：列**不带 ingress-lb 标签**的全部（labelSelector "!ingress-lb"）
        return itemsOf(serviceRuntimePort.listConfigMapsWithoutIngressLb(namespace));
    }

    @Override
    public com.csm.paas.core.domain.software.model.entity.ListenBackInfoEntity exportListenConfigs(String namespace) {
        Map<String, Object> tcpListen = null;
        Map<String, Object> udpListen = null;
        List<Map<String, Object>> configMaps;
        try {
            configMaps = serviceRuntimePort.listConfigMapsWithIngressLb(namespace);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取tcp与udp配置组信息失败,异常为:{}", e.getMessage());
            configMaps = List.of();
        }
        for (Map<String, Object> configMap : configMaps) {
            String name = configMapName(configMap);
            Map<String, Object> metadata = asMap(configMap.get("metadata"));
            Map<String, Object> labels = metadata == null ? null : asMap(metadata.get("labels"));
            String ingressLb = labels == null ? null : (String) labels.get("ingress-lb");
            log.info("拿到的ingress-lb的名称:{}", ingressLb);
            if ("true".equals(ingressLb)) {
                continue;
            }
            try {
                String suffixName = ingressLb.split("-")[1];
                if (name != null && name.equals("tcp-services-" + suffixName)) {
                    tcpListen = configMap;
                }
                if (name != null && name.equals("udp-services-" + suffixName)) {
                    udpListen = configMap;
                }
            } catch (Exception e) {
                log.error("分割失败，跳过该configMap,异常为:{}", e.toString());
            }
        }
        return com.csm.paas.core.domain.software.model.entity.ListenBackInfoEntity.builder()
                .tcpListen(tcpListen).udpListen(udpListen).ingressList(null)
                .build();
    }

    @Override
    public boolean importConfigMaps(String namespace, List<Map<String, Object>> configMaps) {
        if (configMaps == null || configMaps.isEmpty()) {
            return true;
        }
        for (Map<String, Object> configMap : configMaps) {
            String name = configMapName(configMap);
            boolean exist;
            try {
                exist = serviceRuntimePort.findConfigMap(namespace, name).isPresent();
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("该configMap名称:{}不存在,异常为:{}", name, e.getMessage());
                exist = false;
            }
            // 厂商：替换/创建前清掉这两个字段
            Map<String, Object> metadata = asMap(configMap.get("metadata"));
            if (metadata != null) {
                metadata.remove("resourceVersion");
                metadata.remove("uid");
            }
            try {
                if (exist) {
                    serviceRuntimePort.replaceConfigMapObject(namespace, configMap);
                    log.info("更新服务配置组名称:{}成功", name);
                } else {
                    serviceRuntimePort.createConfigMapObject(namespace, configMap);
                    log.info("新增服务配置组名称:{}成功", name);
                }
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.info("导入configmap配置名称:{}失败，导入下一个,异常为:{}", name, e.getMessage());
            }
        }
        return true;
    }

    /**
     * 厂商 `SoftwareService.importListenConfig:3590-3711`，逐行照抄（含死代码与 NPE 行为）。
     * 规则见 {@code IServiceQueryService.importListenConfig}。
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean importListenConfig(String namespace,
            com.csm.paas.core.domain.software.model.valobj.ListenImportVO listenImport) {
        // ① 找 ingressName：列 ingress-lb 标签的 ConfigMap，取第一个标签值 != "true" 的
        String ingressName = null;
        List<Map<String, Object>> configMaps;
        try {
            configMaps = serviceRuntimePort.listConfigMapsWithIngressLb(namespace);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("获取集群configmap失败,异常为:", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.cluster.lb.info.failed");
        }
        for (Map<String, Object> item : configMaps) {
            Map<String, Object> metadata = asMap(item.get("metadata"));
            Map<String, Object> labels = metadata == null ? null : asMap(metadata.get("labels"));
            String ingressLb = labels == null ? null : (String) labels.get("ingress-lb");
            log.info("获取当前集群ingress-lb名称:{}", ingressLb);
            if ("true".equals(ingressLb)) {
                continue;
            }
            ingressName = ingressLb;
            break;
        }
        String suffixName = null;
        // ② 厂商先 new 一个空 V1Service，读到才替换 ⇒ 读不到时后面 getSpec() 为 null（NPE）
        Map<String, Object> ingressService = new java.util.HashMap<>();
        if (ingressName != null) {
            // 厂商：ingressName.split("-")[1] —— 没有 "-" 时越界（照抄）
            String[] split3 = ingressName.split("-");
            suffixName = split3[1];
            try {
                Map<String, Object> snapshot = serviceRuntimePort.readServiceSnapshot(namespace, ingressName);
                if (snapshot != null) {
                    ingressService = snapshot;
                }
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("获取ingress名称:{}的服务发现失败，异常为:", ingressName, e);
            }
        }
        // ③ 厂商死代码：把 port-0/port-1/port-status/port-health 收进 ports，**之后从不使用**。
        //    但它的 NPE 是可观测的（spec 为 null、ports 为 null 都会 500），所以照抄。
        List<Map<String, Object>> ports = new ArrayList<>();
        Map<String, Object> ingressSpec = asMap(ingressService.get("spec"));
        List<Map<String, Object>> servicePorts = ingressSpec == null
                ? null : (List<Map<String, Object>>) ingressSpec.get("ports");
        for (Map<String, Object> servicePort : servicePorts) {
            String portName = (String) servicePort.get("name");
            if (!"port-0".equals(portName) && !"port-1".equals(portName)
                    && !"port-status".equals(portName) && !"port-health".equals(portName)) {
                continue;
            }
            ports.add(servicePort);
        }
        // ④ tcp
        Map<String, Object> tcpListen = listenImport == null ? null : listenImport.getTcpListen();
        if (tcpListen != null) {
            Map<String, Object> data = asMap(tcpListen.get("data"));
            if (data == null) {
                // 厂商：`return;`——**连 udp 也不再处理**（照抄这个早退）
                return true;
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String tcpPort = entry.getKey();
                String tcpPortInfoValue = (String) entry.getValue();
                // 厂商：split(":", 2)[1] —— 没有冒号时越界（照抄）
                String tcpPorts = tcpPortInfoValue.split(":", 2)[1];
                Map<String, Object> port = new java.util.HashMap<>();
                port.put("name", "port-tcp-" + tcpPort);
                port.put("port", Integer.valueOf(tcpPorts));
                port.put("targetPort", tcpPorts);
                port.put("protocol", "TCP");
                ports.add(port);
            }
            String name = configMapName(tcpListen);
            log.info("导入tcp名称为:{}", name);
            String[] split = name.split("-");
            String tcpPre = split[0] + "-" + split[1];
            String tcpName = tcpPre + "-" + suffixName;
            Map<String, Object> metadata = asMap(tcpListen.get("metadata"));
            metadata.remove("resourceVersion");
            metadata.put("name", tcpName);
            asMap(metadata.get("labels")).put("ingress-lb", ingressName);
            metadata.put("name", tcpName);
            metadata.remove("uid");
            try {
                serviceRuntimePort.replaceConfigMapObject(namespace, tcpListen);
                log.info("更新服务配置组名称:{}成功", name);
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.info("更新Tcpconfigmap配置名称:{}失败，异常为:{}", name, e.getMessage());
            }
        }
        // ⑤ udp（与 tcp 同构）
        Map<String, Object> udpListen = listenImport == null ? null : listenImport.getUdpListen();
        if (udpListen != null) {
            Map<String, Object> data = asMap(udpListen.get("data"));
            if (data == null) {
                return true;
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String udpPort = entry.getKey();
                String udpPortInfoValue = (String) entry.getValue();
                String udpPorts = udpPortInfoValue.split(":", 2)[1];
                Map<String, Object> port = new java.util.HashMap<>();
                port.put("name", "port-udp-" + udpPort);
                port.put("port", Integer.valueOf(udpPorts));
                port.put("targetPort", udpPorts);
                port.put("protocol", "UDP");
                ports.add(port);
            }
            String name = configMapName(udpListen);
            log.info("导入udp名称为:{}", name);
            String[] split = name.split("-");
            String udpPre = split[0] + "-" + split[1];
            String udpName = udpPre + "-" + suffixName;
            Map<String, Object> metadata = asMap(udpListen.get("metadata"));
            metadata.remove("resourceVersion");
            metadata.put("name", udpName);
            asMap(metadata.get("labels")).put("ingress-lb", ingressName);
            metadata.put("name", udpName);
            metadata.remove("uid");
            try {
                serviceRuntimePort.replaceConfigMapObject(namespace, udpListen);
                log.info("更新服务配置组名称:{}成功", name);
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.info("更新Udpconfigmap配置名称:{}失败，异常为:{}", name, e.getMessage());
            }
        }
        // 厂商 importListenConfig 是 void；控制器恒返回 true
        return true;
    }

    /** 厂商 `getStringRandom(length)`：每位 `SecureRandom.nextInt(2)%2==0` 取 `a-z`，否则取 `0-9`。 */
    private static String randomString(int length) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (random.nextInt(2) % 2 == 0) {
                value.append((char) (random.nextInt(26) + 97));
            } else {
                value.append(random.nextInt(10));
            }
        }
        return value.toString();
    }

    /** 厂商的随机端口分配：`rand.nextInt(65535) + 10000`，命中已用则重试，最多 10 次。 */
    private static int randomFreeNodePort(List<Integer> used) {
        java.security.SecureRandom rand = new java.security.SecureRandom();
        for (int j = 0; j < 10; j++) {
            int candidate = rand.nextInt(65535) + 10000;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return rand.nextInt(65535) + 10000;
    }

    @Override
    public Map<String, List<Map<String, Object>>> queryOtherPluginWorkloads() {
        return serviceRuntimePort.listOtherPluginWorkloads();
    }

    @Override
    public java.util.Map<String, Object> scaleService(String namespace, String serviceName, int replicas) {
        java.util.Map<String, Object> patched = null;
        try {
            patched = serviceRuntimePort.patchDeploymentReplicas(namespace, serviceName, replicas);
        } catch (Exception e) {
            log.error("scale deployment error", e);
        }
        if (patched != null) {
            return patched;
        }
        try {
            java.util.Map<String, Object> statefulSet = serviceRuntimePort.patchStatefulSetReplicas(
                    namespace, serviceName, replicas);
            if (statefulSet != null) {
                return statefulSet;
            }
            throw new IllegalStateException("neither Deployment nor StatefulSet patched");
        } catch (Exception e) {
            // 厂商：两者都失败 → SystemException(异常消息) ⇒ 500 / code=-1
            throw new com.csm.paas.core.types.exception.SystemException(e.getMessage());
        }
    }

    @Override
    public boolean updateServicePriority(String namespace, String serviceName, String level) {
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            // 厂商：只读 Deployment，读不到就 400（**没有** StatefulSet 回退，与 hostName 端点不同）
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.change.priority.failed");
        }
        // 厂商：level 空白 → 设成**空串**（不是 null）
        serviceRuntimePort.patchDeploymentPriorityClassName(namespace, serviceName, level == null ? "" : level);
        return true;
    }

    @Override
    public boolean updateServiceHostname(String namespace, String serviceName, String hostName) {
        // 厂商：**读不到 Deployment**（ApiException）才回退；不是"hostname 为空"就回退
        if (!serviceRuntimePort.deploymentExists(namespace, serviceName)) {
            return updateStatefulSetHostname(namespace, serviceName, hostName);
        }
        String current = serviceRuntimePort.deploymentHostname(namespace, serviceName);
        if (isBlank(hostName) && isBlank(current)) {
            return true;   // 厂商：两边都空白 → 静默 no-op
        }
        serviceRuntimePort.patchDeploymentHostname(namespace, serviceName, hostName);
        return true;
    }

    private boolean updateStatefulSetHostname(String namespace, String serviceName, String hostName) {
        String current = serviceRuntimePort.statefulSetHostname(namespace, serviceName);
        if (current == null) {
            // 实测厂商文案：HTTP **400** + `service.not.found.update.host.pod`（不是 500、也不是我先前猜的键名）
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "service.not.found.update.host.pod", serviceName);
        }
        if (isBlank(hostName) && isBlank(current)) {
            return true;
        }
        serviceRuntimePort.patchStatefulSetHostname(namespace, serviceName, hostName);
        return true;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    @Override
    public ServiceListEntity queryServiceAuthList(String namespace, int page, int size, String keyword,
                                                 String authType, String serviceType) {
        // ① 基础过滤：appName 既不是 gaop 也不是 gis-server（与 gaop 端点互补！）
        List<ServiceRuntimeEntity> all = serviceRuntimePort.queryServices(namespace).stream()
                .filter(s -> !"gis-server".equals(s.getAppName()) && !"gaop".equals(s.getAppName()))
                .collect(Collectors.toList());

        // ② keyword 两段式（与 gaop 同款）
        if (keyword != null) {
            List<ServiceRuntimeEntity> byName = all.stream()
                    .filter(s -> s.getName() != null && s.getName().contains(keyword))
                    .collect(Collectors.toList());
            all = byName.isEmpty()
                    ? all.stream().filter(s -> filterServiceName(keyword, s)).collect(Collectors.toList())
                    : byName;
        }

        // ③ authType 过滤（仅在传了 authType 时执行）
        if (authType != null && !authType.isBlank() && !all.isEmpty()) {
            List<Integer> typeList = java.util.Arrays.stream(authType.split(","))
                    .map(String::trim).filter(v -> !v.isEmpty()).map(Integer::valueOf).toList();
            Map<String, ServiceAuthEntity> authByName = serviceAuthRepository.queryAll().stream()
                    .collect(Collectors.toMap(ServiceAuthEntity::getServiceId, Function.identity(), (a, b) -> a));
            all = all.stream().filter(s -> keepByAuthType(s, typeList, authByName.get(s.getName())))
                    .collect(Collectors.toList());
        }

        // ④ 分页 + 装配（与 /v1/service 同一套形状）
        int total = all.size();
        int from = Math.max(0, com.csm.paas.core.types.util.PageUtils.offset(page, size));
        int to = Math.min(total, from + size);
        List<ServiceRuntimeEntity> pageItems = from >= total ? List.of() : all.subList(from, to);
        int running = (int) pageItems.stream().filter(s -> s.getReplicas() != null && s.getReplicas() > 0).count();
        return ServiceListEntity.builder()
                .runningDeployment(running).total(total).count(pageItems.size()).services(pageItems).build();
    }

    /**
     * 厂商 `ServiceController:3524-3555` 的授权状态过滤（逐行照抄，第 2 条按等价式化简）。
     *
     * @param auth 该服务在 `g_service_auth` 里的记录，可能为 null
     */
    private static boolean keepByAuthType(ServiceRuntimeEntity s, List<Integer> typeList, ServiceAuthEntity auth) {
        if (auth != null) {
            return auth.getAuth() != null && typeList.contains(auth.getAuth());
        }
        String serviceTypeName = s.getServiceType();
        if (typeList.size() > 1 && typeList.contains(0) && typeList.contains(1)) {
            return true;
        }
        if (typeList.size() == 1 && typeList.contains(0)) {
            // 厂商原式：serviceTypeName == null || length != 1 || !"前台服务".equals(serviceTypeName)
            // 本分支下 length == 1 恒真 ⇒ 化简为下面这句
            return serviceTypeName == null || !"前台服务".equals(serviceTypeName);
        }
        return typeList.contains(1) && "前台服务".equals(serviceTypeName);
    }

    @Override
    public Map<String, List<String>> queryNodeLabels() {
        return serviceRuntimePort.queryNodeLabels();
    }

    @Override
    public ServiceListEntity queryServiceList(String namespace) {
        List<ServiceRuntimeEntity> services = serviceRuntimePort.queryServices(namespace);

        // 回填授权状态：厂商按服务名去 g_service_auth 取（表空则保持 null，与厂商一致）
        Map<String, ServiceAuthEntity> authByService = serviceAuthRepository.queryAll().stream()
                .collect(Collectors.toMap(ServiceAuthEntity::getServiceId, Function.identity(), (a, b) -> a));
        services.forEach(s -> s.setServiceAuth(authByService.get(s.getName())));
        int running = (int) services.stream()
                .filter(s -> s.getReplicas() != null && s.getReplicas() > 0)
                .count();
        log.info("查询服务列表 namespace:{} 共 {} 个，就绪 {} 个", namespace, services.size(), running);
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(services.size())
                .count(services.size())
                .services(services)
                .build();
    }

    // ================================================================ 最后一批端点（node / patch / serviceInfoByNode）

    /** 厂商 `ServiceController.deleteNode:2886-2902` + `TenxCloudImpl.deleteNode:785-800`。 */
    @Override
    public boolean deleteNode(String nodeName) {
        try {
            serviceRuntimePort.deleteNode(nodeName);
        } catch (Exception e) {
            // 厂商：时速云那层的 WrongArgumentException/SystemException 都被控制器 catch (Exception) 接住，
            // 统一翻成 400 `alert.delete.node.info.error`。
            log.error("删除节点信息出错，异常为", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("alert.delete.node.info.error");
        }
        return true;
    }

    /** 厂商 `ServiceController.updateServiceConfig:1165-1402`（`PUT /v1/service/patch`）。 */
    @Override
    public boolean updateServiceConfig(String namespace, Map<String, Object> deployment,
                                       Map<String, Object> service, Map<String, Object> nodePortService) {
        String registryHost = dockerRegistryPort.registryHost() == null ? "" : dockerRegistryPort.registryHost();
        // 厂商第一步就是 deploymentToYaml(...)：它会**原地改写** deployment（清 strategy/metadata、换镜像 host）。
        // 后面那段 maxSurge/maxUnavailable 的 % 归一化因此永远进不去（见下面注释）。
        Map<String, Object> deploymentDoc = toDeploymentMap(deployment, registryHost);
        String appName = labelOf(deploymentDoc, "system/appName");
        String deploymentName = nameOf(deploymentDoc);

        // 1) 时速云 App 段（本平台等价为原生 K8s 的 existApp/existService/create，见接口 javadoc）
        ensureAppAndDeployment(namespace, appName, deploymentName, deploymentDoc);

        // 2) 读 Deployment：读不到 ⇒ 400（厂商 readNamespacedDeployment 的 ApiException）
        boolean deploymentExists;
        try {
            deploymentExists = serviceRuntimePort.deploymentExists(namespace, deploymentName);
        } catch (Exception e) {
            log.error("获取Deployment失败，异常为：", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.deployment.failed");
        }
        if (!deploymentExists) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.deployment.failed");
        }
        Map<String, Object> spec = asMap(deploymentDoc.get("spec"));
        // 照抄的死代码：spec.strategy 已在 toDeploymentMap 里被置 null ⇒ 这段永远不会执行。
        Object strategy = spec == null ? null : spec.get("strategy");
        if (strategy != null) {
            normalizeRollingUpdateStrategy(asMap(strategy));
        }
        Map<String, Object> metadata = asMap(deploymentDoc.get("metadata"));
        Map<String, Object> annotations = metadata == null ? null : asMap(metadata.get("annotations"));
        if (annotations != null) {
            if (annotations.get("kubectl.kubernetes.io/last-applied-configuration") != null) {
                annotations.put("kubectl.kubernetes.io/last-applied-configuration", null);
            }
            if (annotations.get("rollingupdate/target") != null) {
                annotations.put("rollingupdate/target", null);
            }
        }
        Map<String, Object> doc = generateDeploymentDoc(deploymentDoc, registryHost);
        try {
            serviceRuntimePort.replaceDeploymentSnapshot(namespace, doc);
        } catch (com.csm.paas.core.types.exception.K8sApiException e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.change.config.failed");
        } catch (Exception e) {
            log.error("导入服务Deployment失败", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.import.service.deployment.error");
        }

        // 3) Service 段
        if (service != null) {
            String serviceName = nameOf(service);
            Map<String, Object> existingService;
            try {
                existingService = serviceRuntimePort.readServiceSnapshot(namespace, serviceName);
            } catch (Exception e) {
                log.error("获取service失败，异常为：", e);
                throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.service.failed");
            }
            if (existingService == null) {
                throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.service.failed");
            }
            String nodePortServiceName = serviceName + "-nodeport";
            // 厂商：读 `<name>-nodeport` 的 ApiException 只记日志（此时 ports 保持 null ⇒ 走创建分支）。
            Map<String, Object> existingNodePort = null;
            try {
                existingNodePort = serviceRuntimePort.readServiceSnapshot(namespace, nodePortServiceName);
            } catch (Exception e) {
                log.error("查询nodePoft服务未找到的异常:", e);
                log.info("未找到该服务{}的nodeport服务发现", serviceName);
            }
            List<Map<String, Object>> existingPorts = existingNodePort == null ? null : portsOf(existingNodePort);
            if (nodePortService != null) {
                if (existingPorts != null) {
                    List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports = new ArrayList<>();
                    for (Map<String, Object> p : portsOf(nodePortService)) {
                        ports.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                                .appProtocol(strOf(p.get("appProtocol")))
                                .name(strOf(p.get("name")))
                                .port(intOf(p.get("port")))
                                .nodePort(intOf(p.get("nodePort")))
                                .protocol(strOf(p.get("protocol")))
                                .targetPort(intOf(p.get("targetPort")))
                                .build());
                    }
                    try {
                        // 厂商：selector 置 {name: 主服务名}、type 置 NodePort，再整份 merge-patch；
                        // 已存在的 nodeport 服务本来就是这个 selector，所以这里传 type=NodePort 等价。
                        serviceRuntimePort.patchServicePorts(namespace, nodePortServiceName, "NodePort", ports);
                    } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                        log.error("调用API失败: {}", e.getMessage());
                        throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.call.api.failed");
                    } catch (Exception e) {
                        log.error("导入服务service失败", e);
                        throw new com.csm.paas.core.types.exception.WrongArgumentException(
                                "service.import.service.service.error");
                    }
                } else {
                    // 厂商 AppService.generateServiceDoc(nodePortServiceName, deployment 的 labels, containers)
                    List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports = new ArrayList<>();
                    List<Map<String, Object>> requestPorts = portsOf(nodePortService);
                    for (int i = 0; i < requestPorts.size(); i++) {
                        Map<String, Object> p = requestPorts.get(i);
                        Integer port = intOf(p.get("port"));
                        String protocol = strOf(p.get("protocol"));
                        ports.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                                .name(protocol == null ? null
                                        : protocol.toLowerCase() + "-" + nodePortServiceName + "-" + i)
                                .protocol(protocol)
                                .port(port)
                                .targetPort(port)
                                .build());
                    }
                    try {
                        serviceRuntimePort.createNodePortService(namespace, nodePortServiceName, "NodePort",
                                ports, deploymentName);
                    } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                        log.error("调用API失败: {}", e.getMessage());
                        throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.call.api.failed");
                    } catch (Exception e) {
                        log.error("创建服务失败", e);
                        throw new com.csm.paas.core.types.exception.WrongArgumentException("service.create.failed");
                    }
                }
            } else if (existingNodePort != null) {
                try {
                    serviceRuntimePort.deleteService(namespace, nodePortServiceName);
                } catch (Exception e) {
                    // 厂商对 ApiException 只记日志（不报错）。
                    log.error("删除服务失败, serviceName = {}, response = {}", nodePortServiceName, e.getMessage());
                }
            }
            List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> mainPorts = new ArrayList<>();
            for (Map<String, Object> p : portsOf(service)) {
                mainPorts.add(com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO.builder()
                        .protocol(strOf(p.get("protocol")))
                        .name(strOf(p.get("name")))
                        .nodePort(intOf(p.get("nodePort")))
                        .port(intOf(p.get("port")))
                        .targetPort(intOf(p.get("targetPort")))
                        .appProtocol(null)
                        .build());
            }
            Map<String, Object> existingSpec = asMap(existingService.get("spec"));
            String type = existingSpec == null ? null : strOf(existingSpec.get("type"));
            try {
                serviceRuntimePort.patchServicePorts(namespace, serviceName, type, mainPorts);
            } catch (com.csm.paas.core.types.exception.K8sApiException e) {
                log.error("调用API失败: {}", e.getMessage());
                throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.call.api.failed");
            } catch (Exception e) {
                log.error("导入服务service失败", e);
                throw new com.csm.paas.core.types.exception.WrongArgumentException(
                        "service.import.service.service.error");
            }
        }
        return true;
    }

    /** 厂商 `AppService.deploymentToMap:225-280` 的等价实现（**原地改写**入参）。 */
    private Map<String, Object> toDeploymentMap(Map<String, Object> deployment, String registryHost) {
        Map<String, Object> spec = asMap(deployment.get("spec"));
        Map<String, Object> template = spec == null ? null : asMap(spec.get("template"));
        Map<String, Object> podSpec = template == null ? null : asMap(template.get("spec"));
        if (podSpec != null) {
            replaceImageHosts(castList(podSpec.get("containers")), registryHost);
            replaceImageHosts(castList(podSpec.get("initContainers")), registryHost);
        }
        Map<String, Object> metadata = asMap(deployment.get("metadata"));
        if (metadata != null) {
            metadata.put("resourceVersion", null);
            metadata.put("creationTimestamp", null);
            metadata.put("selfLink", null);
            metadata.put("uid", null);
            metadata.put("namespace", null);
        }
        Map<String, Object> templateMetadata = template == null ? null : asMap(template.get("metadata"));
        Map<String, Object> annotations = templateMetadata == null ? null : asMap(templateMetadata.get("annotations"));
        if (annotations != null && annotations.containsKey("gaop/nodeIndex")) {
            Integer index = Integer.valueOf(String.valueOf(annotations.get("gaop/nodeIndex")));
            String indexName = nodeIndexName(index);
            if ("-1".equals(indexName)) {
                podSpec.put("nodeSelector", null);
            } else {
                Map<String, Object> nodeSelector = podSpec == null ? null : asMap(podSpec.get("nodeSelector"));
                if (nodeSelector != null) {
                    log.info("未获取到导入节点调度绑定节点");
                    nodeSelector.put("kubernetes.io/hostname", indexName);
                    nodeSelector.put("beta.kubernetes.io/os", "linux");
                } else {
                    log.info("未拿到node节点绑定设置默认调度");
                    podSpec.put("nodeSelector", null);
                }
            }
            annotations.put("gaop/nodeIndex", null);
        }
        // 厂商 `deploymentToMap:265`：整份清掉 strategy（后面那段 % 归一化因此是死代码）。
        if (spec != null) {
            spec.put("strategy", null);
        }
        deployment.put("apiVersion", "apps/v1");
        deployment.put("kind", "Deployment");
        // 厂商 deleteFeilds:282-293：managedFields 里"fields 为 null"的条目把该键删掉（等价 no-op）。
        if (metadata != null) {
            List<Map<String, Object>> managedFields = castList(metadata.get("managedFields"));
            if (managedFields != null) {
                for (Map<String, Object> managedField : managedFields) {
                    if (managedField.get("fields") == null) {
                        managedField.remove("fields");
                    }
                }
            }
        }
        return deployment;
    }

    /** 厂商 `AppService.generateDeploymentDoc:396-429`（+ 控制器的 `setApiVersion("apps/v1")`）。 */
    private Map<String, Object> generateDeploymentDoc(Map<String, Object> deployment, String registryHost) {
        Map<String, Object> spec = asMap(deployment.get("spec"));
        Map<String, Object> template = spec == null ? null : asMap(spec.get("template"));
        Map<String, Object> podSpec = template == null ? null : asMap(template.get("spec"));
        if (podSpec != null) {
            replaceImageHosts(castList(podSpec.get("containers")), registryHost);
            replaceImageHosts(castList(podSpec.get("initContainers")), registryHost);
        }
        Map<String, Object> metadata = asMap(deployment.get("metadata"));
        if (metadata != null) {
            metadata.put("resourceVersion", null);
            metadata.put("creationTimestamp", null);
            metadata.put("selfLink", null);
            metadata.put("uid", null);
            metadata.put("namespace", null);
        }
        if (podSpec != null) {
            Map<String, Object> nodeSelector = asMap(podSpec.get("nodeSelector"));
            if (nodeSelector == null) {
                nodeSelector = new java.util.LinkedHashMap<>();
                podSpec.put("nodeSelector", nodeSelector);
            }
            nodeSelector.put("beta.kubernetes.io/os", "linux");
        }
        deployment.put("apiVersion", "apps/v1");
        deployment.put("kind", "Deployment");
        return deployment;
    }

    /** 厂商 `AppService.replaceHost:487-496`：把镜像前缀里的 `<ip>:<port>` 换成镜像仓库地址。 */
    private static String replaceHost(String imageFullName, String registryHost) {
        if (imageFullName == null) {
            // 厂商这里会 NPE（Pattern.matcher(null)）⇒ 500；照抄会故意留一个 NPE，故按"不改"处理。
            return null;
        }
        java.util.regex.Matcher matcher = IMAGE_HOST_PATTERN.matcher(imageFullName);
        if (matcher.lookingAt()) {
            String registry = matcher.group(1);
            return imageFullName.replace(registry, registryHost);
        }
        return imageFullName;
    }

    private static void replaceImageHosts(List<Map<String, Object>> containers, String registryHost) {
        if (containers == null) {
            return;
        }
        for (Map<String, Object> container : containers) {
            Object image = container.get("image");
            if (image instanceof String) {
                container.put("image", replaceHost((String) image, registryHost));
            }
        }
    }

    /** 厂商 `AppService.getNdoeIndexName:295-327`（`getNodeInfo()` 失败 ⇒ `-1`）。 */
    private String nodeIndexName(Integer index) {
        if (index != null && index == -1) {
            return "-1";
        }
        List<String> nameList = new ArrayList<>();
        try {
            for (com.csm.paas.core.domain.software.model.valobj.NodeInfoVO node : serviceRuntimePort.listNodes()) {
                nameList.add(node.getName());
            }
        } catch (Exception e) {
            log.error("调用tenxcloud获取node信息出现错误，导入或导出服务配置时出现异常:", e);
            return "-1";
        }
        java.util.Collections.sort(nameList);
        if (index != null && index < nameList.size()) {
            return nameList.get(index);
        }
        log.info("导入节点数量比当前节点多，无法获取下标节点名称");
        return "-1";
    }

    /**
     * 厂商的"时速云 App"段（`existApp`/`existService`/`createApp`/`createDeploymentYaml`）在
     * `TenxCloudImpl` 里其实全是**原生 K8s**实现，这里按同一净效果复刻：
     * `existApp:1119-1132` = 按 `system/appName` 列 Deployment；`existService:2367-2396` = 按
     * `system/svcName` 列 Deployment/**StatefulSet**；`createApp:2553-2572` = 把 YAML 拆成
     * Deployment + Service 两份分别 create。
     *
     * <p><b>本实现的已知简化</b>：`serviceExists` 只看 **Deployment**（`listDeploymentSnapshots`），
     * 没查 StatefulSet——所以"StatefulSet 带 `system/svcName` 但 Deployment 不存在"这种组合下，
     * 我们会多试一次 create（厂商会跳过），create 冲突时同样落 400 `service.create.failed`。
     */
    private void ensureAppAndDeployment(String namespace, String appName, String deploymentName,
                                        Map<String, Object> deploymentDoc) {
        boolean appExists = false;
        boolean serviceExists = false;
        for (Map<String, Object> d : serviceRuntimePort.listDeploymentSnapshots(namespace)) {
            Map<String, String> labels = labelsOf(d);
            if (appName != null && appName.equals(labels.get("system/appName"))) {
                appExists = true;
            }
            if (deploymentName != null && deploymentName.equals(labels.get("system/svcName"))) {
                serviceExists = true;
            }
        }
        if (appExists) {
            if (serviceExists) {
                log.debug("已存在 service: {}，不创建该服务", deploymentName);
                return;
            }
            log.info("不存在服务:{}，开始创建", deploymentName);
            createDeploymentForPatch(namespace, deploymentDoc);
            return;
        }
        log.info("不存在apps应用服务，开始进行创建apps");
        createDeploymentForPatch(namespace, deploymentDoc);
        createServiceForPatch(namespace, deploymentName, deploymentDoc);
    }

    private void createDeploymentForPatch(String namespace, Map<String, Object> deploymentDoc) {
        try {
            serviceRuntimePort.createDeploymentSnapshot(namespace, deploymentDoc);
        } catch (Exception e) {
            log.error("创建服务失败", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.create.failed");
        }
    }

    /**
     * 厂商 `createApp` 的第二半（`TenxService.createServiceYaml:1152-1178` + `AppService.test:353-394`）：
     * labels 被改写成 `{name, system/appName, system/svcName}` 且 `system/appType=business`。
     */
    private void createServiceForPatch(String namespace, String deploymentName, Map<String, Object> deploymentDoc) {
        Map<String, Object> spec = asMap(deploymentDoc.get("spec"));
        Map<String, Object> template = spec == null ? null : asMap(spec.get("template"));
        Map<String, Object> podSpec = template == null ? null : asMap(template.get("spec"));
        List<Map<String, Object>> containers = podSpec == null ? null : castList(podSpec.get("containers"));
        List<Map<String, Object>> ports = new ArrayList<>();
        if (containers != null && !containers.isEmpty()) {
            List<Map<String, Object>> containerPorts = castList(containers.get(0).get("ports"));
            if (containerPorts != null) {
                for (int i = 0; i < containerPorts.size(); i++) {
                    Integer port = intOf(containerPorts.get(i).get("containerPort"));
                    String protocol = strOf(containerPorts.get(i).get("protocol"));
                    Map<String, Object> p = new java.util.LinkedHashMap<>();
                    p.put("name", protocol == null ? null
                            : protocol.toLowerCase() + "-" + deploymentName + "-" + i);
                    p.put("protocol", protocol);
                    p.put("port", port);
                    p.put("targetPort", port);
                    ports.add(p);
                }
            }
        }
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("name", deploymentName);
        labels.put("system/appName", deploymentName);
        labels.put("system/svcName", deploymentName);
        labels.put("system/appType", "business");
        Map<String, String> annotations = new java.util.LinkedHashMap<>();
        annotations.put("system/lbgroup", "none");
        Map<String, String> selector = new java.util.LinkedHashMap<>();
        selector.put("name", deploymentName);

        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("name", deploymentName);
        metadata.put("labels", labels);
        metadata.put("annotations", annotations);
        Map<String, Object> serviceSpec = new java.util.LinkedHashMap<>();
        serviceSpec.put("ports", ports);
        serviceSpec.put("selector", selector);
        Map<String, Object> serviceDoc = new java.util.LinkedHashMap<>();
        serviceDoc.put("kind", "Service");
        serviceDoc.put("apiVersion", "v1");
        serviceDoc.put("metadata", metadata);
        serviceDoc.put("spec", serviceSpec);
        try {
            serviceRuntimePort.createServiceSnapshot(namespace, serviceDoc);
        } catch (Exception e) {
            log.error("创建服务失败", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("service.create.failed");
        }
    }

    /** 厂商那段（因 `strategy` 已被清空而不可达的）`maxSurge`/`maxUnavailable` 补 `%` 逻辑。 */
    private void normalizeRollingUpdateStrategy(Map<String, Object> strategy) {
        if (strategy == null) {
            return;
        }
        boolean endSurge = false;
        boolean endUnavailable = false;
        if ("RollingUpdate".equals(strOf(strategy.get("type")))) {
            Map<String, Object> rolling = asMap(strategy.get("rollingUpdate"));
            if (rolling != null) {
                String maxSurge = strOf(rolling.get("maxSurge"));
                String maxUnavailable = strOf(rolling.get("maxUnavailable"));
                endSurge = maxSurge != null && maxSurge.endsWith("%");
                endUnavailable = maxUnavailable != null && maxUnavailable.endsWith("%");
            }
        }
        Map<String, Object> rollingUpdate = asMap(strategy.get("rollingUpdate"));
        if (rollingUpdate != null) {
            String maxSurge = strOf(rollingUpdate.get("maxSurge"));
            if (!endSurge) {
                rollingUpdate.put("maxSurge", maxSurge + "%");
            }
            String maxUnavailable = strOf(rollingUpdate.get("maxUnavailable"));
            if (!endUnavailable) {
                rollingUpdate.put("maxUnavailable", maxUnavailable + "%");
            }
        }
    }

    // ---------------------------------------------------------------- serviceInfoByNode（厂商 :1691-1810）

    /** 厂商 `ServiceController.getServiceListByNode:1691-1728`。 */
    @Override
    public com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO queryServiceInfoByNode(
            String namespace, String nodeName, String keyword, Integer order, int page, int size) {
        String masterNodeName;
        try {
            masterNodeName = masterNodeName();
        } catch (Exception e) {
            log.error("获取节点信息错误，异常为:", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException("k8s.get.node.info.failed");
        }
        // 厂商用 masterNodeName 拼 Caffeine 缓存 key（30 秒）；本平台不做缓存（差异只在新鲜度）。
        List<com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO> itemList =
                getServiceNodeItems(namespace, nodeName, keyword, masterNodeName);
        if (!itemList.isEmpty() && order != null) {
            if (order == 1) {
                itemList.sort(java.util.Comparator.comparingDouble(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> sumCpuOrder(o)));
            } else if (order == 2) {
                itemList.sort(java.util.Comparator.comparingDouble(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> sumCpuOrder(o)).reversed());
            } else if (order == 3) {
                itemList.sort(java.util.Comparator.comparingDouble(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> sumMemoryOrder(o)));
            } else if (order == 4) {
                itemList.sort(java.util.Comparator.comparingDouble(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> sumMemoryOrder(o)).reversed());
            } else if (order == 5) {
                itemList.sort(java.util.Comparator.comparing(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> o.getGpuOrder()));
            } else if (order == 6) {
                itemList.sort(java.util.Comparator.comparing(
                        (com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO o) -> o.getGpuOrder()).reversed());
            }
        }
        long start = System.currentTimeMillis();
        com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO serviceList =
                paginateServiceNodeItems(itemList, page, size);
        log.info("调用serviceInfoByNode接口花费时间:{}毫秒", System.currentTimeMillis() - start);
        return serviceList;
    }

    /** 厂商 `getMasterNodeName:203-218`。 */
    private String masterNodeName() {
        List<com.csm.paas.core.domain.software.model.valobj.NodeInfoVO> items = serviceRuntimePort.listNodes();
        if (items.size() == 1) {
            return items.get(0).getName();
        }
        for (com.csm.paas.core.domain.software.model.valobj.NodeInfoVO item : items) {
            Map<String, String> labels = item.getLabels();
            String roleLabel = "node-role.kubernetes.io/" + nodeLabel;
            if (labels == null || !labels.containsKey(roleLabel)) {
                continue;
            }
            log.info("拿到master节点的名称为:{}", item.getName());
            return item.getName();
        }
        return null;
    }

    /** 厂商 `getServicesItems:1730-1810` + `getServiceItemInfo:1812-1873`。 */
    private List<com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO> getServiceNodeItems(
            String namespace, String nodeName, String keyword, String masterNodeName) {
        log.info("查询所有节点的pod 参数 nodeName={} keyword={} master={}", nodeName, keyword, masterNodeName);
        Map<String, com.csm.paas.core.domain.node.model.valobj.ServiceNodeBelongPodVO> podBelongInfo =
                new java.util.LinkedHashMap<>();
        Map<String, List<String>> serviceBelongPodList = new java.util.LinkedHashMap<>();
        for (Map<String, Object> pod : serviceRuntimePort.listAllPods(namespace)) {
            Map<String, Object> podSpec = asMap(pod.get("spec"));
            String podNodeName = podSpec == null ? null : strOf(podSpec.get("nodeName"));
            // 厂商用 fieldSelector `spec.nodeName=<nodeName>`（服务端过滤）；这里等价地在客户端过滤。
            if (nodeName != null && !nodeName.isEmpty() && !nodeName.equals(podNodeName)) {
                continue;
            }
            Map<String, Object> podMetadata = asMap(pod.get("metadata"));
            String podName = podMetadata == null ? null : strOf(podMetadata.get("name"));
            var belongPodInfo = com.csm.paas.core.domain.node.model.valobj.ServiceNodeBelongPodVO.builder()
                    .podName(podName)
                    .nodeName(podNodeName)
                    .build();
            Integer restartCount = 0;
            String serviceName = "";
            List<Map<String, Object>> containers = podSpec == null ? null : castList(podSpec.get("containers"));
            if (containers != null && !containers.isEmpty()) {
                serviceName = strOf(containers.get(0).get("name"));
            }
            Map<String, Object> status = asMap(pod.get("status"));
            List<Map<String, Object>> containerStatuses = status == null ? null : castList(status.get("containerStatuses"));
            if (containerStatuses != null) {
                Integer count = intOf(containerStatuses.get(0).get("restartCount"));
                if (count != null) {
                    restartCount = count;
                }
            }
            belongPodInfo.setServiceName(serviceName);
            belongPodInfo.setRestartCount(restartCount);
            podBelongInfo.put(podName, belongPodInfo);
            serviceBelongPodList.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(podName);
        }
        java.util.Set<String> serviceNamed = serviceBelongPodList.keySet();

        // 厂商数据源是 tenxCloud.allPageService(null)（外部客户端，会翻完所有页）；
        // 本平台用同命名空间 Deployment/StatefulSet 直读（不分页，见 queryAllServices）。
        List<ServiceRuntimeEntity> items = serviceRuntimePort.queryAllServices(namespace);
        // 授权状态：与列表端点同口径（厂商 allPageService 出来的项也带 serviceAuth）。
        Map<String, ServiceAuthEntity> authByService = serviceAuthRepository.queryAll().stream()
                .collect(Collectors.toMap(ServiceAuthEntity::getServiceId, Function.identity(), (a, b) -> a));
        items.forEach(s -> s.setServiceAuth(authByService.get(s.getName())));
        List<ServiceRuntimeEntity> servicesItemList = new ArrayList<>();
        for (ServiceRuntimeEntity item : items) {
            String label = item.getServiceLabel();
            if (label != null && !label.trim().isEmpty()
                    && item.getName() != null && serviceNamed.contains(item.getName())) {
                servicesItemList.add(item);
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            List<ServiceRuntimeEntity> filtered = new ArrayList<>();
            for (ServiceRuntimeEntity item : servicesItemList) {
                String name = item.getName();
                String label = item.getServiceLabel();
                if ((name != null && name.contains(keyword)) || (label != null && label.contains(keyword))) {
                    filtered.add(item);
                }
            }
            servicesItemList = filtered;
        }

        // 厂商 24 小时窗口 + 每服务并发补齐（本平台顺序执行，结果一致、只是慢一些）。
        java.time.Instant end = java.time.Instant.now();
        java.time.Instant start = end.minus(1L, java.time.temporal.ChronoUnit.DAYS);
        long startSeconds = start.toEpochMilli() / 1000L;
        long endSeconds = end.toEpochMilli() / 1000L;
        List<com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO> out = new ArrayList<>();
        for (ServiceRuntimeEntity servicesItem : servicesItemList) {
            var nodeItem = com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO.builder()
                    .runtime(servicesItem)
                    .cpuOrder(0.0)
                    .memoryOrder(0.0)
                    .gpuOrder(0.0)
                    .build();
            out.add(nodeItem);
            try {
                fillServiceNodeItem(nodeItem, podBelongInfo, serviceBelongPodList, startSeconds, endSeconds);
            } catch (Exception e) {
                // 厂商是 CompletableFuture 里 catch (Exception) 只记日志 ⇒ 该项保持部分字段。
                log.error("获取服务[{}]监控信息失败", servicesItem.getName(), e);
            }
        }
        return out;
    }

    /** 厂商 `getServiceItemInfo:1812-1873` + `getPodCpuInfo:1899-1921` + `getPodMemoryInfo:1875-1897`。 */
    private void fillServiceNodeItem(com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO nodeItem,
                                     Map<String, com.csm.paas.core.domain.node.model.valobj.ServiceNodeBelongPodVO> podBelongInfo,
                                     Map<String, List<String>> serviceBelongPodList,
                                     long startSecond, long endSecond) {
        ServiceRuntimeEntity servicesItem = nodeItem.getRuntime();
        Map<String, Object> deployment = servicesItem.getDeploymentSnapshot();
        Map<String, Object> resources = container0Resources(deployment);
        if (resources != null) {
            Map<String, Object> limits = asMap(resources.get("limits"));
            if (limits != null) {
                String gpuConfigSize = strOf(limits.get("nvidia.com/gpu"));
                if (gpuConfigSize != null) {
                    nodeItem.setGpuOrder(Double.valueOf(gpuConfigSize));
                }
            }
            Map<String, Object> requests = asMap(resources.get("requests"));
            if (requests != null) {
                // 照抄：厂商对 requestCpu 不判空 ⇒ 缺键 NPE（由调用处的 catch 吞掉，该项保持部分字段）。
                String requestCpu = strOf(requests.get("cpu"));
                if (!requestCpu.endsWith("m")) {
                    double cpuSize = Double.parseDouble(requestCpu);
                    nodeItem.setCpuOrder(cpuSize * 1024.0);
                } else {
                    String cpuInfo = requestCpu.replace("m", "");
                    nodeItem.setCpuOrder(Double.valueOf(cpuInfo));
                }
            }
            if (requests != null) {
                String requestMemory = strOf(requests.get("memory"));
                if (requestMemory.endsWith("Mi")) {
                    String memoryInfo = requestMemory.replace("Mi", "");
                    nodeItem.setMemoryOrder(Double.valueOf(memoryInfo));
                } else {
                    String memoryInfo = requestMemory.replace("Gi", "");
                    double memorySize = Double.parseDouble(memoryInfo);
                    nodeItem.setMemoryOrder(memorySize * 1024.0);
                }
            }
        }
        String name = servicesItem.getName();
        // 厂商传的 podNames 恒为空列表（照抄）。
        List<String> podInfoList = new ArrayList<>();
        List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> cpu =
                serviceCpuMonitorForNodeItem(name, startSecond, endSecond, podInfoList);
        List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> memory =
                serviceMemoryMonitorForNodeItem(name, startSecond, endSecond, podInfoList);

        Map<String, com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO> podMap =
                new java.util.LinkedHashMap<>();
        getPodCpuInfo(podMap, name, cpu);
        getPodMemoryInfo(podMap, name, memory);
        nodeItem.setPodInfoList(new ArrayList<>(podMap.values()));

        java.util.Set<String> belongNodeHashSet = new java.util.LinkedHashSet<>();
        Integer maxCount = 0;
        for (String p : serviceBelongPodList.getOrDefault(name, List.of())) {
            var belongPodInfo = podBelongInfo.get(p);
            if (belongPodInfo == null) {
                continue;
            }
            String infoNodeName = belongPodInfo.getNodeName();
            if (infoNodeName != null && !infoNodeName.trim().isEmpty()) {
                belongNodeHashSet.add(infoNodeName);
            }
            if (belongPodInfo.getRestartCount() != null && belongPodInfo.getRestartCount() > maxCount) {
                maxCount = belongPodInfo.getRestartCount();
            }
        }
        nodeItem.setRestartCount(maxCount);
        nodeItem.setBelongNodeList(new ArrayList<>(belongNodeHashSet));
    }

    /** 厂商 `getPodCpuInfo:1899-1921`：所有采样点扁平化后取 max/min/avg，挂到 `podName=<Deployment 名>` 一项上。 */
    private void getPodCpuInfo(Map<String, com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO> podMap,
                               String name,
                               List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> monitors) {
        try {
            List<com.csm.paas.core.domain.node.model.valobj.MonitorSeriesVO.Point> metrics = new ArrayList<>();
            for (var monitor : monitors) {
                if (monitor.getMetrics() != null) {
                    metrics.addAll(monitor.getMetrics());
                }
            }
            var info = podMap.computeIfAbsent(name,
                    k -> com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO.builder().build());
            info.setPodName(name);
            info.setCpuMaxUsed(metrics.stream()
                    .mapToDouble(n -> Double.parseDouble(n.getValue())).max().orElse(0.0));
            info.setCpuMinUsed(metrics.stream()
                    .mapToDouble(n -> Double.parseDouble(n.getValue())).min().orElse(0.0));
            info.setCpuAvgUsed(metrics.stream()
                    .mapToDouble(n -> Double.parseDouble(n.getValue())).average().orElse(0.0));
        } catch (Exception e) {
            log.error("获取CPU使用失败", e);
        }
    }

    /** 厂商 `getPodMemoryInfo:1875-1897`：memory 用 `Long.parseLong`（科学计数法会失败 ⇒ 三项保持 null）。 */
    private void getPodMemoryInfo(Map<String, com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO> podMap,
                                  String name,
                                  List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> monitors) {
        try {
            List<com.csm.paas.core.domain.node.model.valobj.MonitorSeriesVO.Point> metrics = new ArrayList<>();
            for (var monitor : monitors) {
                if (monitor.getMetrics() != null) {
                    metrics.addAll(monitor.getMetrics());
                }
            }
            var info = podMap.computeIfAbsent(name,
                    k -> com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO.builder().build());
            info.setMemoryMaxUsed(metrics.stream()
                    .mapToLong(n -> Long.parseLong(n.getValue())).max().orElse(0L));
            info.setMemoryMinUsed(metrics.stream()
                    .mapToLong(n -> Long.parseLong(n.getValue())).min().orElse(0L));
            info.setMemoryAvgUsed(metrics.stream()
                    .mapToDouble(n -> Double.parseDouble(n.getValue())).average().orElse(0.0));
            info.setPodName(name);
        } catch (Exception e) {
            log.error("获取内存使用失败", e);
        }
    }

    /** 厂商 `getServiceCpuMonitor:1090-1099`（`podNames` 为空 ⇒ 正则 `name-.*`）。 */
    private List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> serviceCpuMonitorForNodeItem(
            String name, long start, long end, List<String> podNames) {
        String podNameExp = name + "-.*";
        if (!podNames.isEmpty()) {
            podNameExp = String.join("|", podNames);
        }
        String query = "sum(rate(container_cpu_usage_seconds_total{pod_name=~\"" + podNameExp
                + "\",container_name!=\"\",container_name!=\"POD\"}[5m]))  by (pod) ";
        return toServiceSeriesFromMetricPod(
                prometheusMetricPort.queryRange(query, start, end, stepBySecond(start, end)));
    }

    /** 厂商 `getServiceMemoryMonitor:1101-1109`。 */
    private List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> serviceMemoryMonitorForNodeItem(
            String name, long start, long end, List<String> podNames) {
        String podNameExp = name + "-.*";
        if (!podNames.isEmpty()) {
            podNameExp = String.join("|", podNames);
        }
        String query = "container_memory_usage_bytes{pod=~\"" + podNameExp
                + "\",container_name!=\"\",container_name!=\"POD\"}";
        return toServiceSeriesFromMetricPod(
                prometheusMetricPort.queryRange(query, start, end, stepBySecond(start, end)));
    }

    /** 厂商 `getServiceMonitorInfo:909-955`：`podName = metric.pod`，值取原始串。 */
    private List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> toServiceSeriesFromMetricPod(
            List<com.csm.paas.core.domain.software.adapter.port.IPrometheusMetricPort.RangeSeries> seriesList) {
        List<com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO> out = new ArrayList<>();
        try {
            for (var series : seriesList) {
                String podName = series.metric().get("pod");
                if (podName == null) {
                    throw new NullPointerException("metric.pod 缺失（厂商此处 .toString() 会 NPE）");
                }
                var serviceMonitor = com.csm.paas.core.domain.node.model.valobj.ServiceMonitorSeriesVO.builder()
                        .podName(podName)
                        .metrics(new ArrayList<>())
                        .build();
                for (var sample : series.values()) {
                    serviceMonitor.getMetrics().add(
                            com.csm.paas.core.domain.node.model.valobj.MonitorSeriesVO.Point.builder()
                                    .timestamp(offsetDateTime(sample.timestamp()))
                                    .value(sample.value())
                                    .floatValue(sample.value())
                                    .build());
                }
                out.add(serviceMonitor);
            }
        } catch (Exception e) {
            log.error("请求获取service信息接口出错:", e);
            return new ArrayList<>();
        }
        return out;
    }

    private static String offsetDateTime(long epochSecond) {
        return java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSecond),
                java.time.ZoneId.systemDefault()).toString();
    }

    /** 厂商 `getStepBySecond:1077-1088`。 */
    private static int stepBySecond(long start, long end) {
        if (start >= end) {
            return 60;
        }
        double minStepRequiredSeconds = (double) (end - start) / 10000.0;
        int calculatedStepSeconds = (int) Math.ceil(minStepRequiredSeconds);
        return Math.max(calculatedStepSeconds, 60);
    }

    private static double sumCpuOrder(com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO item) {
        return item.getPodInfoList().stream()
                .map(com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO::getCpuMaxUsed)
                .mapToDouble(Double::doubleValue).sum() / 1000.0;
    }

    private static double sumMemoryOrder(com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO item) {
        return item.getPodInfoList().stream()
                .map(com.csm.paas.core.domain.node.model.valobj.ServiceNodePodVO::getMemoryMaxUsed)
                .mapToDouble(Long::longValue).sum() / 1000.0;
    }

    /** 厂商 `pagination:2136-2158`（`runningDeployment` **从不赋值** ⇒ 恒 0）。 */
    private com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO paginateServiceNodeItems(
            List<com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO> list, int page, int size) {
        boolean hasNextPage = list.size() % size != 0;
        int totalPage = list.size() / size + (hasNextPage ? 1 : 0);
        if (page > totalPage) {
            return com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO.builder()
                    .runningDeployment(0)
                    .services(new ArrayList<>())
                    .count(0)
                    .total(list.size())
                    .build();
        }
        if (list.size() < size) {
            return com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO.builder()
                    .runningDeployment(0)
                    .services(list)
                    .total(list.size())
                    .count(list.size())
                    .build();
        }
        int endIndex = page * size;
        List<com.csm.paas.core.domain.node.model.valobj.ServiceNodeItemVO> sub =
                list.subList((page - 1) * size, Math.min(endIndex, list.size()));
        return com.csm.paas.core.domain.node.model.valobj.ServiceNodeListVO.builder()
                .runningDeployment(0)
                .services(sub)
                .total(list.size())
                .count(sub.size())
                .build();
    }

    /** Deployment 第一个容器的 `resources`（没有容器 ⇒ null）。 */
    private static Map<String, Object> container0Resources(Map<String, Object> deploymentSnapshot) {
        if (deploymentSnapshot == null) {
            return null;
        }
        Map<String, Object> spec = asMap(deploymentSnapshot.get("spec"));
        Map<String, Object> template = spec == null ? null : asMap(spec.get("template"));
        Map<String, Object> podSpec = template == null ? null : asMap(template.get("spec"));
        List<Map<String, Object>> containers = podSpec == null ? null : castList(podSpec.get("containers"));
        if (containers == null || containers.isEmpty()) {
            return null;
        }
        return asMap(containers.get(0).get("resources"));
    }

    // ---------------------------------------------------------------- Map 快照的小工具（`asMap`/`intOf` 复用本类已有实现）

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : null;
    }

    private static String strOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nameOf(Map<String, Object> doc) {
        Map<String, Object> metadata = asMap(doc.get("metadata"));
        return metadata == null ? null : strOf(metadata.get("name"));
    }

    private static Map<String, String> labelsOf(Map<String, Object> snapshot) {
        Map<String, Object> metadata = asMap(snapshot.get("metadata"));
        Object labels = metadata == null ? null : metadata.get("labels");
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (labels instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        }
        return out;
    }

    private static String labelOf(Map<String, Object> doc, String key) {
        return labelsOf(doc).get(key);
    }

    private static List<Map<String, Object>> portsOf(Map<String, Object> serviceDoc) {
        Map<String, Object> spec = asMap(serviceDoc.get("spec"));
        return spec == null ? null : castList(spec.get("ports"));
    }

    private static final java.util.regex.Pattern IMAGE_HOST_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)/*");
}
