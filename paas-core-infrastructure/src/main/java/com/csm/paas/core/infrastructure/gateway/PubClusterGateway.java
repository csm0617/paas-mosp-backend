package com.csm.paas.core.infrastructure.gateway;

import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeployment;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateStatefulSetStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * `/v1/pub` 里 ServiceStatus / patchResource 所需的集群读写（fabric8 7 复刻 io.kubernetes client-java）。
 *
 * <p>厂商出处：`PublicController.getServiceStatusInfo:2448-2584`、
 * `FileService.patchServiceResource:1138-1245`。
 *
 * <p>只做读写、不含业务判断（与 `ServiceRuntimeGateway` 同一约定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubClusterGateway {

    private final KubernetesClient kubernetesClient;

    /** 列命名空间全部 Deployment 快照（厂商 `appsV1Api.listNamespacedDeployment`）。 */
    public List<Map<String, Object>> listDeploymentSnapshots(String namespace) {
        List<Deployment> items = kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems();
        log.info("fabric8 读到 namespace:{} 的 Deployment {} 个", namespace, items.size());
        return items.stream().map(PubClusterGateway::toSnapshot).toList();
    }

    /**
     * 列**全部命名空间**的 pod 快照（厂商 `K8sApiClientManager.getPodList(null)`）。
     * <p>照抄：厂商这个方法读失败**会抛**（`SystemException("k8s.call.api.failed")`），
     * 不像其它读那样吞异常（`K8sApiClientManager.java:111-114`）。
     */
    public List<Map<String, Object>> listAllPodSnapshots() {
        return kubernetesClient.pods().inAnyNamespace().list().getItems().stream()
                .map(PubClusterGateway::toSnapshot).toList();
    }

    /** 读 Deployment 快照；**不存在返回 null**。 */
    public Map<String, Object> readDeploymentSnapshot(String namespace, String name) {
        Deployment d = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
        return d == null ? null : toSnapshot(d);
    }

    /** 读 StatefulSet 快照；**不存在返回 null**。 */
    public Map<String, Object> readStatefulSetSnapshot(String namespace, String name) {
        StatefulSet s = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        return s == null ? null : toSnapshot(s);
    }

    /**
     * 厂商 `FileService.patchServiceResource:1138-1183`（Deployment 分支）。
     *
     * @return false = Deployment 读不到（厂商回退 StatefulSet）
     */
    public boolean patchDeploymentResources(String namespace, PubResourceSpecVO spec) {
        Deployment deployment;
        try {
            deployment = kubernetesClient.apps().deployments().inNamespace(namespace)
                    .withName(spec.getName()).get();
        } catch (Exception e) {
            // 厂商 `catch (ApiException) { log.error("调用接口失败", e); return patchServiceResourceForStatefulSet(form); }`
            log.error("调用接口失败", e);
            return false;
        }
        if (deployment == null) {
            log.error("调用接口失败: Deployment {} 读不到", spec.getName());
            return false;
        }
        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        container.setResources(buildResources(spec));
        deployment.getSpec().getTemplate().getSpec().getContainers().set(0, container);
        // 厂商 `setRollingUpdate(deployment, new IntOrString(0), new IntOrString(1))`：
        // 直接改 spec.strategy（**没做 null 判断**，厂商同样会 NPE）
        var strategy = deployment.getSpec().getStrategy();
        strategy.setType("RollingUpdate");
        RollingUpdateDeployment rollingUpdate = new RollingUpdateDeployment();
        rollingUpdate.setMaxSurge(new IntOrString(0));
        rollingUpdate.setMaxUnavailable(new IntOrString(1));
        strategy.setRollingUpdate(rollingUpdate);
        deployment.getSpec().setStrategy(strategy);
        patchDeployment(namespace, deployment);
        return true;
    }

    /**
     * 厂商 `FileService.patchServiceResourceForStatefulSet:1185-1233`。
     *
     * <p>照抄点：厂商这里也构造了 `maxSurge = new IntOrString(0)` 但**从未使用**（死代码，
     * `FileService:1218`）——所以 StatefulSet 的 rollingUpdate **只写 maxUnavailable**。
     *
     * @return false = StatefulSet 读不到（厂商 ⇒ 400 `service.get.service.info.update.resource.failed`）
     */
    public boolean patchStatefulSetResources(String namespace, PubResourceSpecVO spec) {
        StatefulSet statefulSet;
        try {
            statefulSet = kubernetesClient.apps().statefulSets().inNamespace(namespace)
                    .withName(spec.getName()).get();
        } catch (Exception e) {
            // 厂商 `catch (ApiException) { log.error("调用接口失败", e); throw new WrongArgumentException("service.get.service.info.update.resource.failed"); }`
            log.error("调用接口失败", e);
            return false;
        }
        if (statefulSet == null) {
            log.error("调用接口失败: StatefulSet {} 读不到", spec.getName());
            return false;
        }
        var container = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0);
        container.setResources(buildResources(spec));
        statefulSet.getSpec().getTemplate().getSpec().getContainers().set(0, container);
        var strategy = statefulSet.getSpec().getUpdateStrategy();
        strategy.setType("RollingUpdate");
        RollingUpdateStatefulSetStrategy rollingUpdate = new RollingUpdateStatefulSetStrategy();
        rollingUpdate.setMaxUnavailable(new IntOrString(1));
        strategy.setRollingUpdate(rollingUpdate);
        statefulSet.getSpec().setUpdateStrategy(strategy);
        patchStatefulSet(namespace, statefulSet);
        return true;
    }

    /**
     * 厂商两处都构造同一份 `V1ResourceRequirements`：
     * limits 写 `cpu`/`memory`（`gpu` 非空时额外写 `nvidia.com/gpu`），requests 只写 `cpu`/`memory`。
     */
    private static ResourceRequirements buildResources(PubResourceSpecVO spec) {
        ResourceRequirements resources = new ResourceRequirements();
        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity(spec.getLimitCpu()));
        limits.put("memory", new Quantity(spec.getLimitMemory()));
        if (spec.getLimitGpu() != null) {
            limits.put("nvidia.com/gpu", new Quantity(spec.getLimitGpu()));
        }
        resources.setLimits(limits);
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity(spec.getRequestCpu()));
        requests.put("memory", new Quantity(spec.getRequestMemory()));
        resources.setRequests(requests);
        return resources;
    }

    /** 厂商 `PatchUtils.patch(..., "application/merge-patch+json", ...)`：整份对象的 merge-patch。 */
    private void patchDeployment(String namespace, Deployment deployment) {
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(deployment.getMetadata().getName())
                .patch(PatchContext.of(PatchType.JSON_MERGE), Serialization.asJson(deployment));
    }

    private void patchStatefulSet(String namespace, StatefulSet statefulSet) {
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(statefulSet.getMetadata().getName())
                .patch(PatchContext.of(PatchType.JSON_MERGE), Serialization.asJson(statefulSet));
    }

    /** 与 `ServiceRuntimeGateway.toSnapshot` 同款：fabric8 模型 → `Map` 快照。 */
    public static Map<String, Object> toSnapshot(Object k8sObject) {
        try {
            return Serialization.jsonMapper().readValue(Serialization.asJson(k8sObject), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("K8s 对象快照序列化失败", e);
        }
    }
}
