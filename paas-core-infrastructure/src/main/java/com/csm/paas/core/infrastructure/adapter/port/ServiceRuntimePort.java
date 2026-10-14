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
}
