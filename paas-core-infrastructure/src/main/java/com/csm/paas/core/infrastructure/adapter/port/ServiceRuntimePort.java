package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port 实现：只调 gateway，把 K8s 对象翻成领域实体（防腐层）。
 * <p><b>列表是 workload 驱动</b>：厂商按 StatefulSet→Deployment 遍历再找同名 Service
 * （{@code TenxService.java:2307-2342}），没有同名 Service 的 workload **照样入列**（service 为空）；
 * 这正是厂商结果里没有 {@code gaop-mysql}（ExternalName、无工作负载）的原因。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRuntimePort implements IServiceRuntimePort {

    /** 厂商默认页大小（{@code page.setSize(10)}）。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ServiceRuntimeGateway serviceRuntimeGateway;

    @Override
    public void patchDeploymentServiceLabel(String namespace, String serviceKey, String name) {
        serviceRuntimeGateway.patchDeploymentServiceLabel(namespace, serviceKey, name);
    }

    @Override
    public void patchStatefulSetServiceLabel(String namespace, String serviceKey, String name) {
        serviceRuntimeGateway.patchStatefulSetServiceLabel(namespace, serviceKey, name);
    }

    @Override
    public boolean deploymentExists(String namespace, String serviceName) {
        return serviceRuntimeGateway.deploymentExists(namespace, serviceName);
    }

    @Override
    public String deploymentHostname(String namespace, String serviceName) {
        return serviceRuntimeGateway.deploymentHostname(namespace, serviceName);
    }

    @Override
    public void patchDeploymentHostname(String namespace, String serviceName, String hostname) {
        serviceRuntimeGateway.patchDeploymentHostname(namespace, serviceName, hostname);
    }

    @Override
    public String statefulSetHostname(String namespace, String serviceName) {
        return serviceRuntimeGateway.statefulSetHostname(namespace, serviceName);
    }

    @Override
    public void patchStatefulSetHostname(String namespace, String serviceName, String hostname) {
        serviceRuntimeGateway.patchStatefulSetHostname(namespace, serviceName, hostname);
    }

    @Override
    public void patchDeploymentPriorityClassName(String namespace, String serviceName, String level) {
        serviceRuntimeGateway.patchDeploymentPriorityClassName(namespace, serviceName, level);
    }

    @Override
    public Map<String, Object> patchDeploymentReplicas(String namespace, String serviceName, int replicas) {
        return serviceRuntimeGateway.patchDeploymentReplicas(namespace, serviceName, replicas);
    }

    @Override
    public Map<String, Object> patchStatefulSetReplicas(String namespace, String serviceName, int replicas) {
        return serviceRuntimeGateway.patchStatefulSetReplicas(namespace, serviceName, replicas);
    }

    @Override
    public Map<String, List<Map<String, Object>>> listOtherPluginWorkloads() {
        return serviceRuntimeGateway.listOtherPluginWorkloads();
    }

    @Override
    public boolean patchDeploymentCommand(String namespace, String serviceName, List<String> command,
                                          List<String> args) {
        return serviceRuntimeGateway.patchDeploymentCommand(namespace, serviceName, command, args);
    }

    @Override
    public boolean cancelDeploymentStartup(String namespace, String serviceName) {
        return serviceRuntimeGateway.cancelDeploymentStartup(namespace, serviceName);
    }

    @Override
    public boolean setDeploymentStartup(String namespace, String serviceName, long delaySeconds,
                                        String harborHost, String initImage) {
        return serviceRuntimeGateway.setDeploymentStartup(namespace, serviceName, delaySeconds, harborHost, initImage);
    }

    @Override
    public Map<String, List<String>> queryNodeLabels() {
        // 厂商：遍历每个节点的 metadata.labels，按 label 键把"值"塞进一个 set（HashMap 聚合，顺序不可依赖）
        Map<String, java.util.Set<String>> collected = new java.util.HashMap<>();
        for (io.fabric8.kubernetes.api.model.Node node : serviceRuntimeGateway.listNodes()) {
            if (node.getMetadata() == null || node.getMetadata().getLabels() == null) {
                continue;   // 厂商对 labels 为 null 是 catch(NullPointerException) 后忽略
            }
            node.getMetadata().getLabels().forEach((k, v) -> {
                java.util.Set<String> values = collected.computeIfAbsent(k, key -> new java.util.LinkedHashSet<>());
                if (v != null) {
                    values.add(v);
                }
            });
        }
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        collected.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }

    @Override
    public List<ServiceRuntimeEntity> queryServices(String namespace) {
        Map<String, Service> serviceByName = new LinkedHashMap<>();
        for (Service svc : serviceRuntimeGateway.listServices(namespace)) {
            serviceByName.put(svc.getMetadata().getName(), svc);
        }

        List<ServiceRuntimeEntity> workloads = new ArrayList<>();
        for (StatefulSet ss : serviceRuntimeGateway.listStatefulSets(namespace)) {
            workloads.add(toEntity(namespace, ss.getMetadata().getName(),
                    ss.getSpec() != null ? ss.getSpec().getReplicas() : null,
                    serviceByName.get(ss.getMetadata().getName()),
                    serviceRuntimeGateway.toSnapshot(ss)));
        }
        for (Deployment d : serviceRuntimeGateway.listDeployments(namespace)) {
            workloads.add(toEntity(namespace, d.getMetadata().getName(),
                    d.getSpec() != null ? d.getSpec().getReplicas() : null,
                    serviceByName.get(d.getMetadata().getName()),
                    serviceRuntimeGateway.toSnapshot(d)));
        }

        // 厂商顺序：按 workload 的创建时间倒序，再取首页（TenxService.java:2356）
        return workloads.stream()
                .sorted(Comparator.comparing(ServiceRuntimeEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DEFAULT_PAGE_SIZE)
                .toList();
    }

    /**
     * 从 workload 快照里取标签 `system/appType`（厂商 `GET /v1/service/gaop` 的过滤依据）。
     * <p>标签名为厂商常量 `AppTypeLabelsEnum.TYPE_LABEL_NAME`，取值 `gaop-core`/`gaop-base`/`business`。
     */
    @SuppressWarnings("unchecked")
    private static String labelOf(Map<String, Object> workloadSnapshot, String labelKey) {
        if (workloadSnapshot == null) {
            return null;
        }
        Object metadata = workloadSnapshot.get("metadata");
        if (!(metadata instanceof Map)) {
            return null;
        }
        Object labels = ((Map<String, Object>) metadata).get("labels");
        if (!(labels instanceof Map)) {
            return null;   // 厂商对 labels 为 null 的条目直接丢弃
        }
        Object value = ((Map<String, Object>) labels).get(labelKey);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从工作负载快照里取注解 `spec.template.metadata.annotations["gaop/serviceLabel"]`
     * （厂商 `filterServiceName` 用它做 keyword 兜底匹配；`updateName` 改的也是它）。
     */
    @SuppressWarnings("unchecked")
    private static String annotationOf(Map<String, Object> workloadSnapshot, String annotationKey) {
        if (workloadSnapshot == null) {
            return null;
        }
        Object spec = workloadSnapshot.get("spec");
        if (!(spec instanceof Map)) {
            return null;
        }
        Object template = ((Map<String, Object>) spec).get("template");
        if (!(template instanceof Map)) {
            return null;
        }
        Object metadata = ((Map<String, Object>) template).get("metadata");
        if (!(metadata instanceof Map)) {
            return null;
        }
        Object annotations = ((Map<String, Object>) metadata).get("annotations");
        if (!(annotations instanceof Map)) {
            return null;   // 厂商：annotations 为 null → false
        }
        Object value = ((Map<String, Object>) annotations).get(annotationKey);
        return value == null ? null : String.valueOf(value);
    }

    /** 从 workload 快照里取容器镜像（厂商 `imageInfo`）。 */
    @SuppressWarnings("unchecked")
    private String imageOf(Map<String, Object> snapshot) {
        Object spec = snapshot.get("spec");
        if (!(spec instanceof Map<?, ?> specMap)) {
            return null;
        }
        Object template = specMap.get("template");
        if (!(template instanceof Map<?, ?> templateMap)) {
            return null;
        }
        Object podSpec = templateMap.get("spec");
        if (!(podSpec instanceof Map<?, ?> podSpecMap)) {
            return null;
        }
        Object containers = podSpecMap.get("containers");
        if (!(containers instanceof java.util.List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> container)) {
            return null;
        }
        Object image = container.get("image");
        return image instanceof String str ? str : null;
    }

    /** 从原样快照里取创建时间（用于厂商那套"按创建时间倒序"）。 */
    private String creationTimestampOf(Map<String, Object> snapshot) {
        Object metadata = snapshot.get("metadata");
        return metadata instanceof Map<?, ?> map ? (String) map.get("creationTimestamp") : null;
    }

    private ServiceRuntimeEntity toEntity(String namespace, String name, Integer replicas,
                                          Service svc, Map<String, Object> workloadSnapshot) {
        ServicePort firstPort = null;
        if (svc != null && svc.getSpec() != null && svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty()) {
            firstPort = svc.getSpec().getPorts().get(0);
        }

        String targetPort = null;
        if (firstPort != null && firstPort.getTargetPort() != null) {
            // 具名端口要原样保留字符串形态
            targetPort = firstPort.getTargetPort().getStrVal() != null
                    ? firstPort.getTargetPort().getStrVal()
                    : String.valueOf(firstPort.getTargetPort().getIntVal());
        }

        return ServiceRuntimeEntity.builder()
                .name(name)
                .namespace(namespace)
                .type(svc != null && svc.getSpec() != null ? svc.getSpec().getType() : null)
                .portName(firstPort != null ? firstPort.getName() : null)
                .port(firstPort != null ? firstPort.getPort() : null)
                .targetPort(targetPort)
                .protocol(firstPort != null ? firstPort.getProtocol() : null)
                .replicas(replicas)
                .image(imageOf(workloadSnapshot))
                .createdAt(creationTimestampOf(workloadSnapshot))
                .serviceSnapshot(svc != null ? serviceRuntimeGateway.toSnapshot(svc) : null)
                .appType(labelOf(workloadSnapshot, "system/appType"))
                .appName(labelOf(workloadSnapshot, "system/appName"))
                .serviceLabel(annotationOf(workloadSnapshot, "gaop/serviceLabel"))
                .serviceType(annotationOf(workloadSnapshot, "gaop/serviceType"))
                .deploymentSnapshot(workloadSnapshot)
                .build();
    }
}
