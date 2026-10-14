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

    /** 该命名空间**所有** Service 已占用的 nodePort（厂商 `getNodePortUsed()`）。 */
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
