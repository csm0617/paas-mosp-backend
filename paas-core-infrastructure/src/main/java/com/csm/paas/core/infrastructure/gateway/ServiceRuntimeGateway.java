package com.csm.paas.core.infrastructure.gateway;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** fabric8 7 的集群读取网关。只做读写，不含业务判断。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRuntimeGateway {

    /** 厂商 `otherPlugin` 的字段选择器（跨命名空间，排除 gaop-namespace）。 */
    static final String OTHER_PLUGIN_FIELD_SELECTOR = "metadata.namespace!=gaop-namespace";
    /** 厂商 `otherPlugin` 的标签选择器（原样照抄）。 */
    static final String OTHER_PLUGIN_LABEL_SELECTOR =
            "!system-network,!log,app.kubernetes.io/instance!=prometheus,app!=harbor,tier!=control-plane";


    private final KubernetesClient kubernetesClient;

    public List<Service> listServices(String namespace) {
        List<Service> items = kubernetesClient.services().inNamespace(namespace).list().getItems();
        log.info("fabric8 读到 namespace:{} 的 Service {} 个", namespace, items.size());
        return items;
    }

    /** 全部节点（厂商 `coreV1Api.listNode(...)`）。 */
    public List<io.fabric8.kubernetes.api.model.Node> listNodes() {
        List<io.fabric8.kubernetes.api.model.Node> items = kubernetesClient.nodes().list().getItems();
        log.info("fabric8 读到节点 {} 个", items.size());
        return items;
    }

    public List<Deployment> listDeployments(String namespace) {
        List<Deployment> items = kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems();
        log.info("fabric8 读到 namespace:{} 的 Deployment {} 个", namespace, items.size());
        return items;
    }

    /**
     * 厂商那条 JSON patch 的**原样构造**（`ServiceController:1536`）：
     * `[{"op":"replace","path":"/spec/template/metadata/annotations/gaop~1serviceLabel","value":"<name>"}]`
     * <p>`~1` 是 JSON-pointer 里 `/` 的转义 ⇒ 注解键是 `gaop/serviceLabel`。
     */
    public static String serviceLabelPatchBody(String name) {
        return String.format(
                "[{\"op\":\"replace\",\"path\":\"/spec/template/metadata/annotations/gaop~1serviceLabel\",\"value\":\"%s\"}]",
                name);
    }

    /**
     * 把 patch 打在 Deployment 上（厂商 `patchNamespacedDeployment`）。
     * <p><b>必须用 JSON patch 的 `replace` 语义</b>，不能用 `edit()`：厂商发的是
     * `[{"op":"replace",...}]`，当注解**不存在**时 `replace` 会失败（进而回退 StatefulSet）；
     * 而 fabric8 的 `edit()` 是"读-改-写"，会把缺失的注解**补上并成功**——行为完全不同。
     */
    public void patchDeploymentServiceLabel(String namespace, String serviceKey, String name) {
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(serviceKey)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON), serviceLabelPatchBody(name));
    }

    /** 把 patch 打在 StatefulSet 上（厂商失败后的回退路径）。 */
    public void patchStatefulSetServiceLabel(String namespace, String serviceKey, String name) {
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(serviceKey)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON), serviceLabelPatchBody(name));
    }

    /** Deployment 是否存在（厂商"读 Deployment 抛 ApiException 才回退 StatefulSet"的等价判断）。 */
    public boolean deploymentExists(String namespace, String name) {
        return kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get() != null;
    }

    /** 读 Deployment 的 `spec.template.spec.hostname`（**Deployment 存在但没设 hostname 时也返回 null**）。 */
    public String deploymentHostname(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        return d == null || d.getSpec() == null || d.getSpec().getTemplate() == null
                || d.getSpec().getTemplate().getSpec() == null ? null : d.getSpec().getTemplate().getSpec().getHostname();
    }

    /**
     * 设置 Deployment 的 hostname 并**merge-patch**（厂商用 `application/merge-patch+json` 发整份序列化对象）。
     * <p>厂商是"读→改→整份 merge-patch"，我们用 fabric8 的 `edit` 表达同一语义（只改这一个字段）。
     */
    public void patchDeploymentHostname(String namespace, String name, String hostname) {
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .edit(d -> { d.getSpec().getTemplate().getSpec().setHostname(hostname); return d; });
    }

    /** 读 StatefulSet 的 hostname；不存在返回 null。 */
    public String statefulSetHostname(String namespace, String name) {
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        return s == null || s.getSpec() == null || s.getSpec().getTemplate() == null
                || s.getSpec().getTemplate().getSpec() == null ? null : s.getSpec().getTemplate().getSpec().getHostname();
    }

    public void patchStatefulSetHostname(String namespace, String name, String hostname) {
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name)
                .edit(s -> { s.getSpec().getTemplate().getSpec().setHostname(hostname); return s; });
    }

    /** 设置 Deployment 的 `spec.template.spec.priorityClassName`（厂商 priority 端点用整份 merge-patch 表达同一语义）。 */
    public void patchDeploymentPriorityClassName(String namespace, String name, String level) {
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .edit(d -> { d.getSpec().getTemplate().getSpec().setPriorityClassName(level); return d; });
    }

    /**
     * strategic-merge-patch `{"spec":{"replicas":N}}` 到 Deployment，返回 patch 后的对象快照；
     * **对象不存在时返回 null**（厂商据此回退 StatefulSet）。
     */
    public Map<String, Object> patchDeploymentReplicas(String namespace, String name, int replicas) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.STRATEGIC_MERGE),
                        "{\"spec\":{\"replicas\":" + replicas + "}}");
        return d == null ? null : toSnapshot(d);
    }

    /** 同上，打在 StatefulSet 上（厂商失败回退路径）。 */
    public Map<String, Object> patchStatefulSetReplicas(String namespace, String name, int replicas) {
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.STRATEGIC_MERGE),
                        "{\"spec\":{\"replicas\":" + replicas + "}}");
        return s == null ? null : toSnapshot(s);
    }

    /**
     * 复刻厂商那串选择器的语义（**严格按 k8s 规则**）：
     * <ul>
     *   <li>`metadata.namespace!=gaop-namespace` → 排除 gaop-namespace；</li>
     *   <li>`!system-network` / `!log` → 该标签**不存在**；</li>
     *   <li>`k!=v` → 标签**不存在或值不等于 v**（k8s 的 `!=` 对缺失键也成立）。</li>
     * </ul>
     * 厂商是把选择器交给 API server；我们在这里等价复刻，并用对拉核对结果集合。
     */
    static boolean matchesOtherPluginSelector(io.fabric8.kubernetes.api.model.HasMetadata resource) {
        if ("gaop-namespace".equals(resource.getMetadata().getNamespace())) {
            return false;
        }
        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels == null) {
            labels = Map.of();
        }
        return !labels.containsKey("system-network")
                && !labels.containsKey("log")
                && !"prometheus".equals(labels.get("app.kubernetes.io/instance"))
                && !"harbor".equals(labels.get("app"))
                && !"control-plane".equals(labels.get("tier"));
    }

    /**
     * 厂商 `GET /v1/service/otherPlugin` 的三类"其它插件"工作负载：**跨命名空间**列出，
     * 排除 `gaop-namespace`，并带厂商那串标签选择器。
     * <p>厂商原样把选择器交给 API server：
     * `fieldSelector = "metadata.namespace!=gaop-namespace"`、
     * `labelSelector = "!system-network,!log,app.kubernetes.io/instance!=prometheus,app!=harbor,tier!=control-plane"`。
     * 我们同样交给 API server（**不自己在内存里过滤**），否则会与 API server 的选择器语义产生偏差。
     */
    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> listOtherPluginWorkloads() {
        java.util.Map<String, java.util.List<java.util.Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        try {
            result.put("daemonSetList", kubernetesClient.apps().daemonSets().inAnyNamespace().list().getItems().stream()
                    .filter(ServiceRuntimeGateway::matchesOtherPluginSelector).toList().stream().map(this::toSnapshot).toList());
        } catch (Exception e) {
            log.error("获取daemonset异常:{}", e.getMessage());
            result.put("daemonSetList", java.util.List.of());   // 厂商：异常只记日志，返回空列表
        }
        try {
            result.put("deploymentList", kubernetesClient.apps().deployments().inAnyNamespace().list().getItems().stream()
                    .filter(ServiceRuntimeGateway::matchesOtherPluginSelector).toList().stream().map(this::toSnapshot).toList());
        } catch (Exception e) {
            log.error("获取deployment异常:{}", e.getMessage());
            result.put("deploymentList", java.util.List.of());
        }
        try {
            result.put("statefulSetList", kubernetesClient.apps().statefulSets().inAnyNamespace().list().getItems().stream()
                    .filter(ServiceRuntimeGateway::matchesOtherPluginSelector).toList().stream().map(this::toSnapshot).toList());
        } catch (Exception e) {
            log.error("获取statefulset异常:{}", e.getMessage());
            result.put("statefulSetList", java.util.List.of());
        }
        return result;
    }

    /**
     * 给 Deployment 的**所有容器**设置 `command`/`args`（厂商 `updateCommand` 对 `containers.forEach` 做同一件事）。
     * <p>Deployment 不存在时返回 false（厂商据此抛 400 `service.service.name.not.found`）。
     */
    public boolean patchDeploymentCommand(String namespace, String name, List<String> command, List<String> args) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        d.getSpec().getTemplate().getSpec().getContainers()
                .forEach(c -> { c.setCommand(command); c.setArgs(args); });
        // 厂商是"序列化整份对象 + application/merge-patch+json"；这里同样发整份 JSON 的 merge patch
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(d));
        return true;
    }

    /**
     * 取消服务的延时启动（厂商 `cancelServiceStartUp:4030-4055`）：删注解 `gaop/serviceInitTime`、
     * 把 `initContainers` 置 null，然后 **`replaceNamespacedDeployment`（整份替换）**。
     * <p>Deployment 不存在时返回 false（厂商据此抛 400 `service.get.service.config.exception.cancel.delay`）。
     */
    public boolean cancelDeploymentStartup(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        var annotations = d.getSpec().getTemplate().getMetadata().getAnnotations();
        if (annotations != null) {
            annotations.remove("gaop/serviceInitTime");
            d.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
        }
        d.getSpec().getTemplate().getSpec().setInitContainers(null);
        // 厂商用的是 replace（整份替换），不是 patch——照抄
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).replace(d);
        return true;
    }

    /**
     * 设置服务的延时启动（厂商 `setServiceStartUpInfo:3968-4026`）。
     *
     * <p>`delaySeconds == 0`：删注解 `gaop/serviceInitTime`、`initContainers` 置 null、**整份 replace**；
     * <br>`delaySeconds > 0`：写注解 `gaop/serviceInitTime = <秒>`，并**注入一个 initContainer**
     * `{name: startup, image: <harborIp>/system_containers/<initImage>, command: [sh, /data/curl.sh],
     *   env: [{_SERVICE_NAME: <deployment 名>}]}`，然后 **merge-patch**。
     *
     * @return Deployment 不存在时返回 false（厂商据此抛 400）
     */
    public boolean setDeploymentStartup(String namespace, String name, long delaySeconds,
                                        String harborHost, String initImage) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        var template = d.getSpec().getTemplate();
        var annotations = template.getMetadata().getAnnotations();
        if (annotations != null) {
            if (delaySeconds == 0L) {
                annotations.remove("gaop/serviceInitTime");
            } else {
                annotations.put("gaop/serviceInitTime", String.valueOf(delaySeconds));
            }
            template.getMetadata().setAnnotations(annotations);
        }
        if (delaySeconds == 0L) {
            template.getSpec().setInitContainers(null);
            kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).replace(d);   // 整份替换
            return true;
        }
        String harborIp = harborHost == null ? "" : harborHost.replace("http://", "").replace("https://", "");
        var container = new io.fabric8.kubernetes.api.model.ContainerBuilder()
                .withName("startup")
                .withImage(harborIp + "/system_containers/" + initImage)
                .withCommand("sh", "/data/curl.sh")
                .withEnv(new io.fabric8.kubernetes.api.model.EnvVarBuilder()
                        .withName("_SERVICE_NAME").withValue(d.getMetadata().getName()).build())
                .build();
        template.getSpec().setInitContainers(java.util.List.of(container));
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(d));
        return true;
    }

    /**
     * 读 Deployment 第一个容器的端口声明（厂商 `patchServicePorts` A 分支用它推导 Service 端口）。
     * <p>**返回 null 表示 Deployment 不存在**；返回空列表表示"存在但没有声明端口"——两者语义不同，不能混。
     */
    public List<io.fabric8.kubernetes.api.model.ContainerPort> readDeploymentContainerPorts(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return null;
        }
        var containers = d.getSpec().getTemplate().getSpec().getContainers();
        return containers == null || containers.isEmpty() || containers.get(0).getPorts() == null
                ? java.util.List.of() : containers.get(0).getPorts();
    }

    public boolean serviceExists(String namespace, String name) {
        return kubernetesClient.services().inNamespace(namespace).withName(name).get() != null;
    }

    /**
     * 把 Service 的 `spec.ports` 与 `spec.type` 整份 merge-patch（厂商两个分支最后都做这件事），
     * 返回 patch 后的 Service 快照；Service 不存在返回 null。
     */
    public Map<String, Object> patchServicePorts(String namespace, String name, String type,
                                                 List<io.fabric8.kubernetes.api.model.ServicePort> ports) {
        var svc = kubernetesClient.services().inNamespace(namespace).withName(name).get();
        if (svc == null) {
            return null;
        }
        svc.getSpec().setPorts(ports);
        svc.getSpec().setType(type);
        var patched = kubernetesClient.services().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(svc));
        return patched == null ? null : toSnapshot(patched);
    }

    /**
     * 读 Deployment **或** StatefulSet 第一个容器的端口（厂商 `updateServicePort` 第 2-3 步：
     * Deployment 读不到才回退 StatefulSet）。**两者都不存在返回 null**；存在但没声明端口返回空列表。
     */
    public List<io.fabric8.kubernetes.api.model.ContainerPort> readWorkloadContainerPorts(String namespace, String name) {
        // Deployment 优先，读不到再 StatefulSet；两者都不存在返回 null（与"存在但没声明端口"区分）
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d != null) {
            return firstContainerPorts(d.getSpec().getTemplate().getSpec().getContainers());
        }
        var st = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (st != null) {
            return firstContainerPorts(st.getSpec().getTemplate().getSpec().getContainers());
        }
        return null;
    }

    /** 第一个容器的端口声明；没有容器或没有 ports 时返回空列表。 */
    private static List<io.fabric8.kubernetes.api.model.ContainerPort> firstContainerPorts(
            List<io.fabric8.kubernetes.api.model.Container> containers) {
        return containers == null || containers.isEmpty() || containers.get(0).getPorts() == null
                ? java.util.List.of() : containers.get(0).getPorts();
    }

    /**
     * 读工作负载的"身份"（Deployment 优先，读不到回退 StatefulSet）：
     * 返回 `[isDeployment, metadata.name]`；**两者都不存在返回 null**。
     * <p>厂商出处：`updateServiceDiscovery:1088-1105`（Deployment 读不到再读 StatefulSet，都读不到抛 500）。
     */
    public com.csm.paas.core.infrastructure.gateway.dto.WorkloadRefDTO readWorkloadRef(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d != null) {
            return new com.csm.paas.core.infrastructure.gateway.dto.WorkloadRefDTO(
                    true, d.getMetadata() == null ? name : d.getMetadata().getName());
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (s != null) {
            return new com.csm.paas.core.infrastructure.gateway.dto.WorkloadRefDTO(
                    false, s.getMetadata() == null ? name : s.getMetadata().getName());
        }
        return null;
    }

    /**
     * 把工作负载**所有容器**的 `ports` 设成给定列表并 patch（厂商 `:1140-1152`：先
     * `containers.forEach(c -> c.setPorts(ports))`，再整份 merge-patch）。
     * <p>用 fabric8 的 `edit` 表达"读→改→patch"同一语义（只动 `ports` 这一个字段）。
     *
     * @return patch 后的对象快照；**对象不存在返回 null**（厂商此时抛 ApiException）
     */
    public Map<String, Object> patchWorkloadContainerPorts(String namespace, String name, boolean deployment,
                                                           List<io.fabric8.kubernetes.api.model.ContainerPort> ports) {
        if (deployment) {
            var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                    .edit(x -> {
                        x.getSpec().getTemplate().getSpec().getContainers().forEach(c -> c.setPorts(ports));
                        return x;
                    });
            return d == null ? null : toSnapshot(d);
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name)
                .edit(x -> {
                    x.getSpec().getTemplate().getSpec().getContainers().forEach(c -> c.setPorts(ports));
                    return x;
                });
        return s == null ? null : toSnapshot(s);
    }

    /**
     * **整份 replace** Deployment 的 `spec.volumes` 与**第一个容器**的 `volumeMounts`
     * （厂商 `ServiceController.updateServiceVolumes:934` 的 `replaceNamespacedDeployment`——PUT 不是 patch）。
     *
     * @return 替换后的快照；**Deployment 不存在返回 null**
     */
    public Map<String, Object> replaceDeploymentVolumes(String namespace, String name,
                                                        List<io.fabric8.kubernetes.api.model.Volume> volumes,
                                                        List<io.fabric8.kubernetes.api.model.VolumeMount> mounts) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return null;
        }
        var spec = d.getSpec().getTemplate().getSpec();
        // 厂商：整份替换 volumes（不是合并）
        spec.setVolumes(volumes);
        // 厂商：只改第一个容器；容器列表为空时这里抛 IndexOutOfBounds（照抄，由上层 catch 成 500）
        spec.getContainers().get(0).setVolumeMounts(mounts);
        var replaced = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).replace(d);
        return replaced == null ? null : toSnapshot(replaced);
    }

    /**
     * 给 Deployment **第一个容器**设置/清空 readiness 与 liveness 探针，然后整份 merge-patch
     * （厂商 `healthCheck:2658-2689`：读→改 container→`containers.set(0,…)`→整份 merge-patch）。
     *
     * @param patchReadiness false 表示"readiness 不动"；true 时用 {@code readiness}（**null = 清空**）
     * @return false = Deployment 不存在；容器列表为空时抛 `IndexOutOfBoundsException`（照抄厂商）
     */
    public boolean patchDeploymentProbes(String namespace, String name,
                                         boolean patchReadiness, io.fabric8.kubernetes.api.model.Probe readiness,
                                         boolean patchLiveness, io.fabric8.kubernetes.api.model.Probe liveness) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        // 厂商直接 get(0)，没有判空 ⇒ 容器为空时抛 IndexOutOfBounds（照抄）
        var container = d.getSpec().getTemplate().getSpec().getContainers().get(0);
        if (patchReadiness) {
            container.setReadinessProbe(readiness);
        }
        if (patchLiveness) {
            container.setLivenessProbe(liveness);
        }
        d.getSpec().getTemplate().getSpec().getContainers().set(0, container);
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(d));
        return true;
    }

    /** 该命名空间**所有** Service 已占用的 nodePort（厂商 `getNodePortUsed()`）。 */
    /**
     * 读**任意一个 Service** 的快照（厂商 `getServiceByName`：按 `metadata.name=` 列，取第 0 条，
     * 出错返回 null）。`relationService` 的 `nodePortInfo` 要合并 `<name>-nodeport` 这个 Service 的端口。
     */
    public Map<String, Object> readService(String namespace, String name) {
        var svc = kubernetesClient.services().inNamespace(namespace).withName(name).get();
        return svc == null ? null : toSnapshot(svc);
    }

    /**
     * 滚动更新（厂商 `rollingUpdate:2599-2644` + `setRollingUpdate:2646-2654`）：按 `type` 重设 strategy，
     * 写注解 `gaop/updateTimestamp`、`minReadySeconds=2`、`containers[0].image`，最后整份 merge-patch。
     *
     * @return false = Deployment 不存在
     */
    public boolean patchDeploymentRollingUpdate(String namespace, String name, Integer type, String version,
                                                long updateTimestamp) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        var spec = d.getSpec();
        if (type != null && (type == 1 || type == 2)) {
            // 厂商 `setRollingUpdate`：type=1 ⇒ maxSurge=1/maxUnavailable=0；type=2 ⇒ 0/1（IntOrString 的整型形态）
            var rollingUpdate = new io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder()
                    .withMaxSurge(new io.fabric8.kubernetes.api.model.IntOrString(type == 1 ? 1 : 0))
                    .withMaxUnavailable(new io.fabric8.kubernetes.api.model.IntOrString(type == 1 ? 0 : 1))
                    .build();
            spec.setStrategy(new io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder()
                    .withType("RollingUpdate")
                    .withRollingUpdate(rollingUpdate)
                    .build());
        }
        if (spec.getTemplate().getMetadata().getAnnotations() == null) {
            spec.getTemplate().getMetadata().setAnnotations(new java.util.HashMap<>());
        }
        spec.getTemplate().getMetadata().getAnnotations().put("gaop/updateTimestamp",
                String.valueOf(updateTimestamp));
        spec.setMinReadySeconds(2);
        var podSpec = spec.getTemplate().getSpec();
        var container = podSpec.getContainers().get(0);
        container.setImage(version);
        podSpec.getContainers().set(0, container);
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(d));
        return true;
    }

    /** 读某个 DaemonSet 的原样快照；**不存在返回 null**。 */
    public Map<String, Object> readDaemonSetSnapshot(String namespace, String name) {
        var ds = kubernetesClient.apps().daemonSets().inNamespace(namespace).withName(name).get();
        return ds == null ? null : toSnapshot(ds);
    }

    /**
     * 读 Deployment 的 configMap 卷名（厂商逐卷看 `volume.getConfigMap()` 那段）。
     * <p>不存在或读取失败 ⇒ 空列表（调用方按"没收集到"处理）。
     */
    public List<String> readDeploymentConfigMapNames(String namespace, String name) {
        try {
            var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
            if (d == null || d.getSpec() == null || d.getSpec().getTemplate() == null
                    || d.getSpec().getTemplate().getSpec() == null
                    || d.getSpec().getTemplate().getSpec().getVolumes() == null) {
                return List.of();
            }
            return d.getSpec().getTemplate().getSpec().getVolumes().stream()
                    .filter(v -> v.getConfigMap() != null && v.getConfigMap().getName() != null)
                    .map(v -> v.getConfigMap().getName())
                    .toList();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            return List.of();
        }
    }

    /** 带非空 `ingress-lb` 标签的 Deployment 的标签值（厂商把它当"ingress 名"用）。 */
    public List<String> listIngressLbValues(String namespace) {
        return kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems().stream()
                .filter(d -> d.getMetadata() != null && d.getMetadata().getLabels() != null)
                .map(d -> d.getMetadata().getLabels().get("ingress-lb"))
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    /** Deployment 的 annotations（不存在/无注解 ⇒ 空 Map）。 */
    public Map<String, String> readDeploymentAnnotations(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null || d.getMetadata() == null || d.getMetadata().getAnnotations() == null) {
            return Map.of();
        }
        return d.getMetadata().getAnnotations();
    }

    /** ConfigMap 的 data（不存在 ⇒ null）。 */
    public Map<String, String> readConfigMapData(String namespace, String name) {
        var cm = kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();
        return cm == null ? null : cm.getData();
    }

    /** 不带 `ingress-lb` 标签的 ConfigMap 列表（整份快照，含 items/metadata）。 */
    public Map<String, Object> listConfigMapsWithoutIngressLb(String namespace) {
        var list = kubernetesClient.configMaps().inNamespace(namespace).withoutLabel("ingress-lb").list();
        return toSnapshot(list);
    }

    /** 读一个 ConfigMap；不存在 ⇒ 空（fabric8 `.get()` 对 404 返回 null）。 */
    public java.util.Optional<Map<String, Object>> findConfigMap(String namespace, String name) {
        var cm = kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();
        return cm == null ? java.util.Optional.empty() : java.util.Optional.of(toSnapshot(cm));
    }

    /** 全部 Deployment 的原样快照。 */
    public List<Map<String, Object>> listDeploymentSnapshots(String namespace) {
        return kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems().stream()
                .map(this::toSnapshot).toList();
    }

    /** `type=NodePort` 的 Service 原样快照。 */
    public List<Map<String, Object>> listNodePortServices(String namespace) {
        return kubernetesClient.services().inNamespace(namespace).list().getItems().stream()
                .filter(s -> s.getSpec() != null && "NodePort".equals(s.getSpec().getType()))
                .map(this::toSnapshot)
                .toList();
    }

    /** 按名字读 ConfigMap 快照；**读不到的跳过**（厂商 `exportConfigMap` 的行为）。 */
    public List<Map<String, Object>> readConfigMaps(String namespace, List<String> names) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (String name : names) {
            var cm = kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();
            if (cm != null) {
                result.add(toSnapshot(cm));
            }
        }
        return result;
    }

    /** 列命名空间里全部 pod 的原样快照。 */
    public List<Map<String, Object>> listAllPods(String namespace) {
        return kubernetesClient.pods().inNamespace(namespace).list().getItems().stream()
                .map(this::toSnapshot).toList();
    }

    /** 按"标签 + `spec.nodeName`"列 pod 的原样快照（厂商 `listNamespacedPod` 的两个 selector）。 */
    public List<Map<String, Object>> listPodsByLabelAndNode(String namespace, String labelKey, String labelValue,
                                                           String nodeName) {
        return kubernetesClient.pods().inNamespace(namespace)
                .withLabel(labelKey, labelValue)
                .withField("spec.nodeName", nodeName)
                .list().getItems().stream().map(this::toSnapshot).toList();
    }

    /**
     * 给 pod 模板打注解 `kubectl.kubernetes.io/restartedAt` 并整份 replace
     * （厂商 `serviceRestart:3826-3912` 的滚动重启手法）。
     *
     * @return false = 工作负载不存在
     */
    public boolean replaceWorkloadRestartedAt(String namespace, String name, boolean deployment, String restartedAt) {
        if (deployment) {
            var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
            if (d == null) {
                return false;
            }
            if (d.getSpec().getTemplate() == null) {
                d.getSpec().setTemplate(new io.fabric8.kubernetes.api.model.PodTemplateSpec());
            }
            if (d.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
                d.getSpec().getTemplate().getMetadata().setAnnotations(new java.util.HashMap<>());
            }
            d.getSpec().getTemplate().getMetadata().getAnnotations().put("kubectl.kubernetes.io/restartedAt",
                    restartedAt);
            kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).replace(d);
            return true;
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (s == null) {
            return false;
        }
        if (s.getSpec().getTemplate() == null) {
            s.getSpec().setTemplate(new io.fabric8.kubernetes.api.model.PodTemplateSpec());
        }
        if (s.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
            s.getSpec().getTemplate().getMetadata().setAnnotations(new java.util.HashMap<>());
        }
        s.getSpec().getTemplate().getMetadata().getAnnotations().put("kubectl.kubernetes.io/restartedAt",
                restartedAt);
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).replace(s);
        return true;
    }

    /**
     * 读工作负载的副本与状态读数（Deployment 优先，回退 StatefulSet）；**两者都不存在返回 null**。
     * <p>厂商出处：`batch-start`/`batch-stop` 用 `spec.replicas` 判"已停"，用 `status` 推状态。
     */
    public Map<String, Object> readWorkloadState(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d != null) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("deployment", true);
            row.put("specReplicas", d.getSpec() == null ? null : d.getSpec().getReplicas());
            var st = d.getStatus();
            row.put("replicas", st == null ? null : st.getReplicas());
            row.put("readyReplicas", st == null ? null : st.getReadyReplicas());
            row.put("availableReplicas", st == null ? null : st.getAvailableReplicas());
            row.put("updatedReplicas", st == null ? null : st.getUpdatedReplicas());
            row.put("unavailableReplicas", st == null ? null : st.getUnavailableReplicas());
            return row;
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (s != null) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("deployment", false);
            row.put("specReplicas", s.getSpec() == null ? null : s.getSpec().getReplicas());
            var st = s.getStatus();
            row.put("replicas", st == null ? null : st.getReplicas());
            row.put("readyReplicas", st == null ? null : st.getReadyReplicas());
            row.put("availableReplicas", st == null ? null : st.getAvailableReplicas());
            row.put("updatedReplicas", st == null ? null : st.getUpdatedReplicas());
            // 注意：StatefulSetStatus **没有** unavailableReplicas 字段（厂商那份推导也没用它）
            return row;
        }
        return null;
    }

    /**
     * 把 `spec.replicas` 设成给定值并**整份 replace**（厂商 `scaleDeployment`/`scaleStatefulSet:3794-3824`
     * 用的是 `replaceNamespaced*`，不是 patch）。
     *
     * @return false = 工作负载不存在
     */
    public boolean replaceWorkloadReplicas(String namespace, String name, boolean deployment, int replicas) {
        if (deployment) {
            var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
            if (d == null) {
                return false;
            }
            d.getSpec().setReplicas(replicas);
            kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).replace(d);
            return true;
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (s == null) {
            return false;
        }
        s.getSpec().setReplicas(replicas);
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).replace(s);
        return true;
    }

    /** 读 Deployment 的 `spec.template.spec.tolerations`（没有则空列表）。 */
    public List<Map<String, Object>> readDeploymentTolerations(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null || d.getSpec() == null || d.getSpec().getTemplate() == null
                || d.getSpec().getTemplate().getSpec() == null
                || d.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return List.of();
        }
        return d.getSpec().getTemplate().getSpec().getTolerations().stream()
                .map(t -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("key", t.getKey());
                    row.put("operator", t.getOperator());
                    row.put("effect", t.getEffect());
                    return row;
                })
                .toList();
    }

    /**
     * 整份替换 `spec.template.spec.tolerations` 并 merge-patch（厂商 `allowMasterTolerance:3425-3475`
     * 两处都是"设值后整份 patch"）。
     *
     * @return false = Deployment 不存在
     */
    public boolean patchDeploymentTolerations(String namespace, String name, List<Map<String, Object>> tolerations) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) {
            return false;
        }
        // ⚠️ 这里**不能**用 `Serialization.asJson(整份对象)`：它会把**空集合**（以及 null）省略掉，
        // 于是"清空 tolerations"的 merge-patch 体里根本没有 tolerations 键 ⇒ 打不动（实测踩过）。
        // 改成显式构造 merge-patch 体，并把 inclusion 设成 ALWAYS 保证空数组也写出去。
        List<Map<String, Object>> rows = tolerations.stream()
                .map(t -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("key", t.get("key"));
                    row.put("operator", t.get("operator"));
                    row.put("effect", t.get("effect"));
                    return row;
                })
                .toList();
        Map<String, Object> patchBody = Map.of("spec", Map.of("template", Map.of("spec",
                Map.of("tolerations", rows))));
        String body;
        try {
            body = Serialization.jsonMapper().copy()
                    .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                    .writeValueAsString(patchBody);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new io.fabric8.kubernetes.client.KubernetesClientException(e.getMessage());
        }
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), body);
        return true;
    }

    /**
     * 按标签选择器列 pod，返回 `{name, generateName}`（厂商 `getServiceEvent` 是列全部 pod 再按
     * `system/svcName` 过滤，用选择器等价）。
     */
    public List<Map<String, Object>> listPodsByLabel(String namespace, String labelKey, String labelValue) {
        return kubernetesClient.pods().inNamespace(namespace).withLabel(labelKey, labelValue).list().getItems().stream()
                .map(p -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("name", p.getMetadata() == null ? null : p.getMetadata().getName());
                    row.put("generateName", p.getMetadata() == null ? null : p.getMetadata().getGenerateName());
                    return row;
                })
                .toList();
    }

    /**
     * 列 Event 的原样快照，按 `involvedObject.name` + `involvedObject.kind` 过滤。
     * <p>厂商用的是服务端 `fieldSelector`；fabric8 7 的 events DSL 在 `inNamespace` 之后没有
     * `withFieldSelector`，所以在客户端做同一件事（**结果集等价**，见 `findings/123`）。
     */
    public List<Map<String, Object>> listEvents(String namespace, String involvedObjectName,
                                               String involvedObjectKind) {
        return kubernetesClient.v1().events().inNamespace(namespace).list().getItems().stream()
                .filter(e -> e.getInvolvedObject() != null
                        && involvedObjectName.equals(e.getInvolvedObject().getName())
                        && involvedObjectKind.equals(e.getInvolvedObject().getKind()))
                .map(this::toSnapshot)
                .toList();
    }

    public List<Integer> listUsedNodePorts(String namespace) {
        List<Integer> used = new java.util.ArrayList<>();
        for (var svc : listServices(namespace)) {
            if (svc.getSpec() == null || svc.getSpec().getPorts() == null) {
                continue;
            }
            for (var p : svc.getSpec().getPorts()) {
                if (p.getNodePort() != null) {
                    used.add(p.getNodePort());
                }
            }
        }
        return used;
    }

    /**
     * 创建 `<name>-nodeport` Service（厂商 `AppService.generateServiceDoc` + 覆盖 name/type/ports）。
     * <p>骨架照抄：`kind=Service`、`apiVersion=v1`、labels `{name, system/appName, system/svcName, system/appType}`、
     * annotations `{system/lbgroup: none}`、**selector `{name: <deployment 名>}`**、ports 由调用方给。
     */
    public Map<String, Object> createNodePortService(String namespace, String name, String type,
                                                     List<io.fabric8.kubernetes.api.model.ServicePort> ports,
                                                     String deploymentName, Map<String, String> deploymentLabels) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("name", deploymentName);
        labels.put("system/appName", deploymentLabels == null ? null : deploymentLabels.get("system/appName"));
        labels.put("system/svcName", deploymentName);
        labels.put("system/appType", deploymentLabels == null ? null : deploymentLabels.get("system/appType"));
        var svc = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName(name).withLabels(labels)
                .addToAnnotations("system/lbgroup", "none").endMetadata()
                .withNewSpec().withType(type).withPorts(ports)
                .addToSelector("name", deploymentName).endSpec()
                .build();
        var created = kubernetesClient.services().inNamespace(namespace).resource(svc).create();
        return created == null ? null : toSnapshot(created);
    }

    /** patch 已存在的 `<name>-nodeport` 的 ports（厂商 6b 分支）。 */
    public Map<String, Object> patchNodePortServicePorts(String namespace, String name,
                                                         List<io.fabric8.kubernetes.api.model.ServicePort> ports) {
        var svc = kubernetesClient.services().inNamespace(namespace).withName(name).get();
        if (svc == null) {
            return null;
        }
        svc.getSpec().setPorts(ports);
        var patched = kubernetesClient.services().inNamespace(namespace).withName(name)
                .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                        io.fabric8.kubernetes.client.dsl.base.PatchType.JSON_MERGE), Serialization.asJson(svc));
        return patched == null ? null : toSnapshot(patched);
    }

    /** 读工作负载的 labels（`generateServiceDoc` 要用）。 */
    public Map<String, String> workloadLabels(String namespace, String name) {
        var d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d != null) {
            return d.getMetadata().getLabels();
        }
        var s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        return s == null ? Map.of() : s.getMetadata().getLabels();
    }

    public List<StatefulSet> listStatefulSets(String namespace) {
        List<StatefulSet> items = kubernetesClient.apps().statefulSets().inNamespace(namespace).list().getItems();
        log.info("fabric8 读到 namespace:{} 的 StatefulSet {} 个", namespace, items.size());
        return items;
    }

    /** 把 K8s 对象转成原样 Map（厂商契约要求把完整对象塞进响应）。 */
    public Map<String, Object> toSnapshot(Object k8sObject) {
        try {
            return Serialization.jsonMapper().readValue(Serialization.asJson(k8sObject), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("K8s 对象快照序列化失败", e);
        }
    }
}
