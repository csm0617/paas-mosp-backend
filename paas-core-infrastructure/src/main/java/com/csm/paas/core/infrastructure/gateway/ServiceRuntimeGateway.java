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
