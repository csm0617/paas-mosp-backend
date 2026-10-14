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
    public List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> readDeploymentContainerPorts(
            String namespace, String serviceName) {
        var ports = serviceRuntimeGateway.readDeploymentContainerPorts(namespace, serviceName);
        if (ports == null) {
            return null;   // Deployment 不存在——与"没有端口"区分开
        }
        return ports.stream().map(p -> com.csm.paas.core.domain.software.model.valobj.ContainerPortVO.builder()
                .protocol(p.getProtocol()).containerPort(p.getContainerPort()).build()).toList();
    }

    @Override
    public boolean serviceExists(String namespace, String serviceName) {
        return serviceRuntimeGateway.serviceExists(namespace, serviceName);
    }

    @Override
    public Map<String, Object> patchServicePorts(String namespace, String serviceName, String type,
                                                 List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports) {
        try {
            return serviceRuntimeGateway.patchServicePorts(namespace, serviceName, type, toK8sPorts(ports));
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            // 分层：domain 不认识 fabric8（原来这里直接漏出去，与本类其它方法不一致）。
            // 消息原样保留——`updateServiceNodePort` 靠 `msg.contains("422")` 判端口占用。
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> readWorkloadContainerPorts(
            String namespace, String serviceName) {
        var ports = serviceRuntimeGateway.readWorkloadContainerPorts(namespace, serviceName);
        if (ports == null) {
            return null;
        }
        return ports.stream().map(p -> com.csm.paas.core.domain.software.model.valobj.ContainerPortVO.builder()
                .protocol(p.getProtocol()).containerPort(p.getContainerPort()).build()).toList();
    }

    @Override
    public Map<String, Object> replaceDeploymentVolumes(
            String namespace, String serviceName,
            List<com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO> volumes,
            List<com.csm.paas.core.domain.software.model.valobj.VolumeMountVO> volumeMounts) {
        List<io.fabric8.kubernetes.api.model.Volume> k8sVolumes = volumes.stream()
                .map(v -> {
                    var builder = new io.fabric8.kubernetes.api.model.VolumeBuilder().withName(v.getName());
                    if (v.getConfigMapName() != null) {
                        builder.withNewConfigMap()
                                .withName(v.getConfigMapName())
                                .withItems(v.getItems() == null ? List.of() : v.getItems().stream()
                                        .map(i -> new io.fabric8.kubernetes.api.model.KeyToPathBuilder()
                                                .withKey(i.getKey()).withPath(i.getPath()).build())
                                        .toList())
                                .endConfigMap();
                    } else {
                        builder.withNewHostPath()
                                .withPath(v.getHostPath())
                                // 厂商：type 是**空串**（不是 null）
                                .withType(v.getHostPathType())
                                .endHostPath();
                    }
                    return builder.build();
                })
                .toList();
        List<io.fabric8.kubernetes.api.model.VolumeMount> k8sMounts = volumeMounts.stream()
                .map(m -> new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                        .withName(m.getName())
                        .withMountPath(m.getMountPath())
                        .withSubPath(m.getSubPath())
                        .withReadOnly(m.getReadOnly())
                        .build())
                .toList();
        try {
            return serviceRuntimeGateway.replaceDeploymentVolumes(namespace, serviceName, k8sVolumes, k8sMounts);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public void patchDeploymentProbes(String namespace, String serviceName,
                                      com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO readiness,
                                      com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO liveness) {
        ProbePatch r = toProbePatch(readiness);
        ProbePatch l = toProbePatch(liveness);
        try {
            if (!serviceRuntimeGateway.patchDeploymentProbes(namespace, serviceName,
                    r.changed(), r.probe(), l.changed(), l.probe())) {
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "deployment " + serviceName + " not found");
            }
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO readWorkloadState(String namespace,
                                                                                           String serviceName) {
        try {
            java.util.Map<String, Object> row = serviceRuntimeGateway.readWorkloadState(namespace, serviceName);
            if (row == null) {
                return null;
            }
            return com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO.builder()
                    .deployment(Boolean.TRUE.equals(row.get("deployment")))
                    .specReplicas(intOf(row.get("specReplicas")))
                    .replicas(intOf(row.get("replicas")))
                    .readyReplicas(intOf(row.get("readyReplicas")))
                    .availableReplicas(intOf(row.get("availableReplicas")))
                    .updatedReplicas(intOf(row.get("updatedReplicas")))
                    .unavailableReplicas(intOf(row.get("unavailableReplicas")))
                    .build();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> readDaemonSetSnapshot(String namespace, String daemonSetName) {
        try {
            return serviceRuntimeGateway.readDaemonSetSnapshot(namespace, daemonSetName);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listPodsByLabelAndNode(String namespace, String labelKey,
                                                                               String labelValue, String nodeName) {
        try {
            return serviceRuntimeGateway.listPodsByLabelAndNode(namespace, labelKey, labelValue, nodeName);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<String> readDeploymentConfigMapNames(String namespace, String serviceName) {
        return serviceRuntimeGateway.readDeploymentConfigMapNames(namespace, serviceName);
    }

    @Override
    public java.util.List<String> readDeploymentConfigMapNamesOrThrow(String namespace, String serviceName) {
        try {
            var d = serviceRuntimeGateway.readDeploymentConfigMapNames(namespace, serviceName);
            if (!serviceRuntimeGateway.deploymentExists(namespace, serviceName)) {
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "deployment " + serviceName + " not found");
            }
            return d;
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<String> listIngressLbValues(String namespace) {
        try {
            return serviceRuntimeGateway.listIngressLbValues(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, String> readDeploymentAnnotations(String namespace, String name) {
        try {
            return serviceRuntimeGateway.readDeploymentAnnotations(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, String> readConfigMapData(String namespace, String name) {
        try {
            return serviceRuntimeGateway.readConfigMapData(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> listConfigMapsWithoutIngressLb(String namespace) {
        try {
            return serviceRuntimeGateway.listConfigMapsWithoutIngressLb(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Optional<java.util.Map<String, Object>> findConfigMap(String namespace, String name) {
        try {
            return serviceRuntimeGateway.findConfigMap(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> createConfigMap(String namespace, String name) {
        try {
            return serviceRuntimeGateway.createConfigMap(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> replaceConfigMapData(String namespace, String name,
                                                              java.util.Map<String, String> data) {
        try {
            return serviceRuntimeGateway.replaceConfigMapData(namespace, name, data);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public boolean deleteConfigMap(String namespace, String name) {
        try {
            return serviceRuntimeGateway.deleteConfigMap(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listConfigMapsWithIngressLb(String namespace) {
        try {
            return serviceRuntimeGateway.listConfigMapsWithIngressLb(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public void createConfigMapObject(String namespace, java.util.Map<String, Object> configMap) {
        try {
            serviceRuntimeGateway.createConfigMapObject(namespace, configMap);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public void replaceConfigMapObject(String namespace, java.util.Map<String, Object> configMap) {
        try {
            serviceRuntimeGateway.replaceConfigMapObject(namespace, configMap);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listDeploymentSnapshots(String namespace) {
        try {
            return serviceRuntimeGateway.listDeploymentSnapshots(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listNodePortServices(String namespace) {
        try {
            return serviceRuntimeGateway.listNodePortServices(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> readConfigMaps(String namespace, java.util.List<String> names) {
        try {
            return serviceRuntimeGateway.readConfigMaps(namespace, names);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listAllPods(String namespace) {
        try {
            return serviceRuntimeGateway.listAllPods(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public boolean replaceWorkloadRestartedAt(String namespace, String serviceName, boolean deployment,
                                              String restartedAt) {
        try {
            return serviceRuntimeGateway.replaceWorkloadRestartedAt(namespace, serviceName, deployment, restartedAt);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public boolean replaceWorkloadReplicas(String namespace, String serviceName, boolean deployment, int replicas) {
        try {
            return serviceRuntimeGateway.replaceWorkloadReplicas(namespace, serviceName, deployment, replicas);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    private static Integer intOf(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.TolerationVO> readDeploymentTolerations(
            String namespace, String serviceName) {
        try {
            return serviceRuntimeGateway.readDeploymentTolerations(namespace, serviceName).stream()
                    .map(row -> com.csm.paas.core.domain.software.model.valobj.TolerationVO.builder()
                            .key((String) row.get("key"))
                            .operator((String) row.get("operator"))
                            .effect((String) row.get("effect"))
                            .build())
                    .toList();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public void patchDeploymentTolerations(String namespace, String serviceName,
                                           java.util.List<com.csm.paas.core.domain.software.model.valobj.TolerationVO> tolerations) {
        List<java.util.Map<String, Object>> rows = tolerations.stream()
                .map(t -> {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("key", t.getKey());
                    row.put("operator", t.getOperator());
                    row.put("effect", t.getEffect());
                    return row;
                })
                .toList();
        try {
            if (!serviceRuntimeGateway.patchDeploymentTolerations(namespace, serviceName, rows)) {
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "deployment " + serviceName + " not found");
            }
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO> listPodsByLabel(
            String namespace, String labelKey, String labelValue) {
        try {
            return serviceRuntimeGateway.listPodsByLabel(namespace, labelKey, labelValue).stream()
                    .map(row -> com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO.builder()
                            .name((String) row.get("name"))
                            .generateName((String) row.get("generateName"))
                            .build())
                    .toList();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listEvents(String namespace, String involvedObjectName,
                                                                   String involvedObjectKind) {
        try {
            return serviceRuntimeGateway.listEvents(namespace, involvedObjectName, involvedObjectKind);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public boolean patchDeploymentRollingUpdate(String namespace, String serviceName, Integer type, String version,
                                                long updateTimestamp) {
        try {
            return serviceRuntimeGateway.patchDeploymentRollingUpdate(
                    namespace, serviceName, type, version, updateTimestamp);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> readServiceSnapshot(String namespace, String serviceName) {
        try {
            return serviceRuntimeGateway.readService(namespace, serviceName);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    // ---------- 服务详情（厂商 GET /v1/service/{serviceName}）----------

    /**
     * 厂商 `getServiceDetail:1406-1437` 的三步：确认工作负载存在 → 在**全部命名空间**的 pod 里
     * 找"第一个容器名 == 服务名"的那个，取 `hostIP` 与 `lastState.terminated` → 取 Deployment 镜像。
     *
     * <p>照抄点：厂商的 pod 扫描 **不按工作负载过滤**、容器名取的是 `containers.get(0).getName()`，
     * 并且找到第一个**容器名匹配**的 pod 就 `break`（不管它的 terminated 是不是 null）。
     * <p>主动收紧（deviations B24）：`containers` 为空时厂商的 `name` 保持 null ⇒ `name.equals(...)` NPE；
     * 我们跳过这种 pod。
     */
    @Override
    public ServiceRuntimeEntity lookupServiceDetail(String namespace, String serviceName) {
        ServiceRuntimeEntity found = null;
        for (Service svc : serviceRuntimeGateway.listServices(namespace)) {
            if (!serviceName.equals(svc.getMetadata().getName())) {
                continue;
            }
            // 厂商 `existService` 看的是 Service；工作负载按同名 Deployment 优先、回退 StatefulSet
            for (var deployment : serviceRuntimeGateway.listDeployments(namespace)) {
                if (serviceName.equals(deployment.getMetadata().getName())) {
                    found = toEntity(namespace, serviceName,
                            deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null,
                            svc, serviceRuntimeGateway.toSnapshot(deployment));
                    break;
                }
            }
            if (found == null) {
                for (var statefulSet : serviceRuntimeGateway.listStatefulSets(namespace)) {
                    if (serviceName.equals(statefulSet.getMetadata().getName())) {
                        found = toEntity(namespace, serviceName,
                                statefulSet.getSpec() != null ? statefulSet.getSpec().getReplicas() : null,
                                svc, serviceRuntimeGateway.toSnapshot(statefulSet));
                        break;
                    }
                }
            }
            break;
        }
        if (found == null) {
            // 厂商：`existService` 判否 ⇒ 400；调用方（domain）负责抛那个 i18n 键
            return null;
        }

        for (Map<String, Object> pod : serviceRuntimeGateway.listAllPodsAllNamespaces()) {
            String containerName = firstContainerName(pod);
            if (containerName == null) {
                log.warn("pod 的 containers 为空，跳过（厂商此处会 NPE，deviations B24）");
                continue;
            }
            if (!containerName.equals(serviceName)) {
                continue;
            }
            Map<String, Object> status = childMap(pod, "status");
            if (status != null) {
                Object hostIp = status.get("hostIP");
                if (hostIp instanceof String str) {
                    found.setHostIP(str);
                }
                Map<String, Object> terminated = firstContainerTerminated(status);
                if (terminated != null) {
                    found.setTerminatedSnapshot(terminated);
                }
            }
            // 厂商找到第一个容器名匹配的 pod 就 break（不管 terminated 是否为空）
            break;
        }

        // `<serviceName>-nodeport` 这个 Service（找不到 ⇒ nodePortServiceSnapshot 为 null）
        found.setNodePortServiceSnapshot(readServiceSnapshot(namespace, serviceName + "-nodeport"));
        return found;
    }

    /** 快照里 `spec.containers[0].name`；列表为空/取不到返回 null。 */
    @SuppressWarnings("unchecked")
    private static String firstContainerName(Map<String, Object> pod) {
        Map<String, Object> spec = childMap(pod, "spec");
        if (spec == null || !(spec.get("containers") instanceof List<?> containers) || containers.isEmpty()) {
            return null;
        }
        Object first = containers.get(0);
        return first instanceof Map<?, ?> c && c.get("name") instanceof String name ? name : null;
    }

    /**
     * 快照里 `status.containerStatuses[0].lastState.terminated`；链上任何一环缺失返回 null
     * （厂商 `:1427` 的条件就是这条链全非 null）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstContainerTerminated(Map<String, Object> status) {
        if (!(status.get("containerStatuses") instanceof List<?> statuses) || statuses.isEmpty()) {
            return null;
        }
        Object first = statuses.get(0);
        if (!(first instanceof Map<?, ?> statusMap)) {
            return null;
        }
        Object lastState = ((Map<String, Object>) statusMap).get("lastState");
        if (!(lastState instanceof Map<?, ?> lastStateMap)) {
            return null;
        }
        Object terminated = ((Map<String, Object>) lastStateMap).get("terminated");
        return terminated instanceof Map<?, ?> t ? (Map<String, Object>) t : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // ---------- updateServiceRole（厂商 PUT /v1/service/updateServiceRole）----------

    @Override
    public boolean patchDeploymentTemplateMetadata(String namespace, String name, Map<String, String> annotations) {
        try {
            return serviceRuntimeGateway.patchWorkloadTemplateAnnotations(namespace, name, true, annotations);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public boolean patchStatefulSetTemplateMetadata(String namespace, String name, Map<String, String> annotations) {
        try {
            return serviceRuntimeGateway.patchWorkloadTemplateAnnotations(namespace, name, false, annotations);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    /**
     * 读工作负载 pod 模板的 `spec.template.metadata.annotations`（Deployment 优先，回退 StatefulSet）。
     * <p>与厂商 `updateServiceRole:1507-1511` 一致：**对象存在但该字段为 null 时也返回 null**
     * （这正是厂商那段"put 被覆盖"缺陷成立的前提）。
     */
    @Override
    public Map<String, String> readWorkloadTemplateAnnotations(String namespace, String name) {
        for (var deployment : serviceRuntimeGateway.listDeployments(namespace)) {
            if (name.equals(deployment.getMetadata().getName())) {
                return templateAnnotationsOf(deployment.getSpec() == null ? null : deployment.getSpec().getTemplate());
            }
        }
        for (var statefulSet : serviceRuntimeGateway.listStatefulSets(namespace)) {
            if (name.equals(statefulSet.getMetadata().getName())) {
                return templateAnnotationsOf(statefulSet.getSpec() == null ? null : statefulSet.getSpec().getTemplate());
            }
        }
        return null;
    }

    private static Map<String, String> templateAnnotationsOf(io.fabric8.kubernetes.api.model.PodTemplateSpec template) {
        if (template == null || template.getMetadata() == null) {
            return null;
        }
        return template.getMetadata().getAnnotations();
    }

    // ---------- schedulingNode / reStartBaseService（SSH，类 C）----------

    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.NodeInfoVO> listNodes() {
        try {
            return serviceRuntimeGateway.listNodesSnapshot().stream()
                    .map(n -> com.csm.paas.core.domain.software.model.valobj.NodeInfoVO.builder()
                            .name(n.getMetadata() == null ? null : n.getMetadata().getName())
                            .labels(n.getMetadata() == null ? null : n.getMetadata().getLabels())
                            .firstAddress(firstNodeAddress(n))
                            .build())
                    .toList();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    /** `status.addresses[0].address`（厂商 `:3110` / `:3335` 直接取第 0 个，不判空）。 */
    private static String firstNodeAddress(io.fabric8.kubernetes.api.model.Node node) {
        if (node.getStatus() == null || node.getStatus().getAddresses() == null
                || node.getStatus().getAddresses().isEmpty()) {
            return null;
        }
        return node.getStatus().getAddresses().get(0).getAddress();
    }

    // ---------- servicePods（厂商 DELETE /v1/service/servicePods）----------

    @Override
    public void deletePod(String namespace, String podName, Boolean force) {
        try {
            serviceRuntimeGateway.deletePod(namespace, podName, force);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    // ---------- resetService（厂商 POST /v1/software/reset_service）----------

    @Override
    public Map<String, Map<String, String>> listDeploymentLabels(String namespace) {
        try {
            return serviceRuntimeGateway.listDeploymentLabels(namespace);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public void deleteDeployment(String namespace, String name) {
        try {
            serviceRuntimeGateway.deleteDeployment(namespace, name);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public String readPodHostIp(String namespace, String podName) {
        try {
            return serviceRuntimeGateway.readPodHostIp(namespace, podName);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public java.math.BigDecimal readNodeAllocatableCpuByInternalIp(String internalIp) {
        try {
            return serviceRuntimeGateway.readNodeAllocatableCpuByInternalIp(internalIp);
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    /** 一个探针的"改不改 + 改成什么"（`null` probe 表示清空）。 */
    private record ProbePatch(boolean changed, io.fabric8.kubernetes.api.model.Probe probe) {
    }

    /**
     * 厂商 `patchReadiness/patchLiveness` 的同一套规则：`type` 0=清空、1=HTTPGet（scheme 写死 HTTP）、
     * 2=TCPSocket、**其它值不动**。
     * <p>厂商写的是 `getType() == 0`（`Integer` 拆箱）⇒ `type` 为 null 时 NPE；我们按"不动"处理
     * （`deviations.md` B16）。
     */
    private static ProbePatch toProbePatch(
            com.csm.paas.core.domain.software.model.valobj.HealthCheckVO.HealthSetVO set) {
        if (set == null || set.getType() == null) {
            return new ProbePatch(false, null);
        }
        if (set.getType() == 0) {
            return new ProbePatch(true, null);
        }
        if (set.getType() != 1 && set.getType() != 2) {
            return new ProbePatch(false, null);
        }
        var builder = new io.fabric8.kubernetes.api.model.ProbeBuilder()
                .withInitialDelaySeconds(set.getInitialDelaySeconds())
                .withTimeoutSeconds(set.getTimeoutSeconds())
                .withPeriodSeconds(set.getPeriodSeconds())
                .withSuccessThreshold(set.getSuccessThreshold())
                .withFailureThreshold(set.getFailureThreshold());
        if (set.getType() == 1) {
            builder.withNewHttpGet()
                    .withPath(set.getPath())
                    .withPort(new io.fabric8.kubernetes.api.model.IntOrString(set.getPort()))
                    // 厂商把 scheme 写死成 "HTTP"
                    .withScheme("HTTP")
                    .endHttpGet();
        } else {
            builder.withNewTcpSocket()
                    .withPort(new io.fabric8.kubernetes.api.model.IntOrString(set.getPort()))
                    .endTcpSocket();
        }
        return new ProbePatch(true, builder.build());
    }

    @Override
    public List<Integer> listUsedNodePorts(String namespace) {
        return serviceRuntimeGateway.listUsedNodePorts(namespace);
    }

    @Override
    public com.csm.paas.core.domain.software.model.valobj.WorkloadRefVO readWorkloadRef(String namespace,
                                                                                        String serviceName) {
        var ref = serviceRuntimeGateway.readWorkloadRef(namespace, serviceName);
        if (ref == null) {
            return null;
        }
        return com.csm.paas.core.domain.software.model.valobj.WorkloadRefVO.builder()
                .deployment(ref.deployment())
                .name(ref.name())
                .build();
    }

    @Override
    public void patchWorkloadContainerPorts(String namespace, String serviceName, boolean deployment,
                                            List<com.csm.paas.core.domain.software.model.valobj.ContainerPortVO> ports) {
        // 厂商只把 protocol 与 containerPort 塞进容器端口（`name` 被丢掉），照抄
        List<io.fabric8.kubernetes.api.model.ContainerPort> k8sPorts = ports.stream()
                .map(p -> new io.fabric8.kubernetes.api.model.ContainerPortBuilder()
                        .withProtocol(p.getProtocol())
                        .withContainerPort(p.getContainerPort())
                        .build())
                .toList();
        try {
            if (serviceRuntimeGateway.patchWorkloadContainerPorts(namespace, serviceName, deployment, k8sPorts) == null) {
                // 厂商：对象不存在时 patch 抛 ApiException ⇒ 由 domain 翻成 400
                throw new com.csm.paas.core.types.exception.K8sApiException(
                        "workload " + serviceName + " not found");
            }
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public Map<String, Object> createNodePortService(String namespace, String name, String type,
                                                     List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports,
                                                     String workloadName) {
        try {
            return serviceRuntimeGateway.createNodePortService(namespace, name, type, toK8sPorts(ports), workloadName,
                    serviceRuntimeGateway.workloadLabels(namespace, workloadName));
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            // 分层：domain 不认识 fabric8，这里翻译成领域可见的 K8sApiException
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    @Override
    public Map<String, Object> patchNodePortServicePorts(String namespace, String name,
                                                         List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports) {
        try {
            return serviceRuntimeGateway.patchNodePortServicePorts(namespace, name, toK8sPorts(ports));
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            throw new com.csm.paas.core.types.exception.K8sApiException(e.getMessage());
        }
    }

    /** 领域端口规格 → K8s ServicePort（两个端点共用）。 */
    private static List<io.fabric8.kubernetes.api.model.ServicePort> toK8sPorts(
            List<com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO> ports) {
        return ports.stream()
                .map(p -> new io.fabric8.kubernetes.api.model.ServicePortBuilder()
                        .withAppProtocol(p.getAppProtocol())
                        .withName(p.getName())
                        .withPort(p.getPort())
                        .withProtocol(p.getProtocol())
                        .withTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(p.getTargetPort()))
                        .withNodePort(p.getNodePort())
                        .build())
                .toList();
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

    // ---------- 作战模式切换（厂商 ModeServiceImpl.getListWithOutGaop / updateServiceEnv）----------

    /**
     * 厂商 `ModeServiceImpl.getListWithOutGaop:229-248` 的过滤 + `startModeType:119-120` 取 env。
     * <p>厂商数据源是腾讯云 API（**类 C 不可验证**）；这里用同集群 Deployment 直读等价复刻其过滤规则。
     */
    @Override
    public List<com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO>
    listBusinessServiceDeploymentEnvs(String namespace) {
        List<Deployment> deployments = serviceRuntimeGateway.listDeployments(namespace);
        List<com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO> result = new ArrayList<>();
        for (Deployment deployment : deployments) {
            if (deployment == null || deployment.getMetadata() == null
                    || deployment.getMetadata().getLabels() == null) {
                continue;
            }
            Map<String, String> labels = deployment.getMetadata().getLabels();
            String appType = labels.get("system/appType");
            boolean keep;
            if (appType != null) {
                // 厂商 AppTypeLabelsEnum.BUSINESS.getType() == "business"
                keep = "business".equals(appType);
            } else {
                String appName = labels.get("system/appName");
                if ("gis-server".equals(appName) || "gis-jb".equals(appName)) {
                    keep = false;
                } else {
                    keep = !"gaop".equals(appName);
                }
            }
            if (!keep) {
                continue;
            }
            // 厂商 containers.get(0).getEnv()：容器列表为空 ⇒ 越界 500（照抄）
            List<io.fabric8.kubernetes.api.model.EnvVar> env = deployment.getSpec().getTemplate().getSpec()
                    .getContainers().get(0).getEnv();
            result.add(com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO.builder()
                    .serviceName(deployment.getMetadata().getName())
                    // 必须是**可变列表**：领域层会按厂商的索引 bug `env.remove(i)` 就地删
                    .env(env == null ? null : env.stream()
                            .map(e -> com.csm.paas.core.domain.software.model.valobj.EnvVarVO.builder()
                                    .name(e.getName()).value(e.getValue()).build())
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                    .build());
        }
        return result;
    }

    /**
     * 厂商 `ModeServiceImpl.updateServiceEnv:162` 的 `readNamespacedDeployment`：
     * Deployment 不存在 ⇒ null；否则返回第一个容器的 env（可能为 null）。
     */
    @Override
    public com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO readDeploymentEnv(
            String namespace, String serviceName) {
        List<io.fabric8.kubernetes.api.model.EnvVar> env =
                serviceRuntimeGateway.readDeploymentEnv(namespace, serviceName);
        if (env == null && !serviceRuntimeGateway.deploymentExists(namespace, serviceName)) {
            return null;
        }
        return com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO.builder()
                .serviceName(serviceName)
                // 可变列表：领域层要 addAll（厂商 envVars.addAll）
                .env(env == null ? null : env.stream()
                        .map(e -> com.csm.paas.core.domain.software.model.valobj.EnvVarVO.builder()
                                .name(e.getName()).value(e.getValue()).build())
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .build();
    }

    /**
     * 厂商 `ModeServiceImpl.updateServiceEnv:176-205` 的最后一步：把第一个容器的 env 整份替换并 patch。
     * 合并逻辑在领域层（`ModeCommandServiceImpl.updateServiceEnv`）。
     */
    @Override
    public boolean replaceDeploymentEnv(String namespace, String serviceName,
            List<com.csm.paas.core.domain.software.model.valobj.EnvVarVO> env) {
        List<io.fabric8.kubernetes.api.model.EnvVar> fabric8Env = env == null ? null : env.stream()
                .map(e -> {
                    io.fabric8.kubernetes.api.model.EnvVar v = new io.fabric8.kubernetes.api.model.EnvVar();
                    v.setName(e.getName());
                    v.setValue(e.getValue());
                    return v;
                })
                .toList();
        return serviceRuntimeGateway.replaceDeploymentEnv(namespace, serviceName, fabric8Env);
    }
}
