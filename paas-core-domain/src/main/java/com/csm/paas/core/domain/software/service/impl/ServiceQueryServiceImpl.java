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
        if (s.isDeployment()) {
            if (s.getAvailableReplicas() != null) {
                if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                    if (s.getUnavailableReplicas() != null
                            && s.getReadyReplicas() > s.getAvailableReplicas()) {
                        return " ROLLING_UPDATE";
                    }
                    return "RUNNING";
                }
            } else {
                if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null
                        && s.getUnavailableReplicas() != null) {
                    return "RUNNING";
                }
                if (s.getReplicas() != null && s.getUpdatedReplicas() != null
                        && s.getUnavailableReplicas() != null) {
                    return "STARING";
                }
            }
            return "STOP";
        }
        if (s.getAvailableReplicas() != null) {
            if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                if (s.getReadyReplicas() > s.getAvailableReplicas()) {
                    return " ROLLING_UPDATE";
                }
                return "RUNNING";
            }
        } else {
            if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                return "RUNNING";
            }
            if (s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                return "STARING";
            }
        }
        return "STOP";
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
}
