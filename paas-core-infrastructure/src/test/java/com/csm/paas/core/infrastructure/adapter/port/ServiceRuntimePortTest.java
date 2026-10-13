package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 端口层契约：**workload 驱动**（厂商 `TenxService.java:2307-2342`）与**具名端口**保真。
 * <p>不需要集群、不需要 Spring：这条缝让"厂商前端一行不改"的门槛可以离线跑。
 */
class ServiceRuntimePortTest {

    private static final String NS = "gaop-namespace";

    private final ServiceRuntimeGateway gateway = mock(ServiceRuntimeGateway.class);

    @Test
    void workloadDrivenListKeepsWorkloadWithoutServiceAndPreservesNamedPort() {
        Deployment withService = deployment("svc-a", 1);
        Deployment withoutService = deployment("svc-b", 0);
        StatefulSet statefulSet = statefulSet("sts-c", 2);
        Service namedPortService = serviceWithNamedPort("svc-a", "http", 8080);

        when(gateway.listServices(NS)).thenReturn(List.of(namedPortService));
        when(gateway.listDeployments(NS)).thenReturn(List.of(withService, withoutService));
        when(gateway.listStatefulSets(NS)).thenReturn(List.of(statefulSet));
        when(gateway.toSnapshot(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> snapshotOf(inv.getArgument(0)));

        List<ServiceRuntimeEntity> result = new ServiceRuntimePort(gateway).queryServices(NS);

        // 三个 workload 全部入列（厂商行为：没有同名 Service 也照样入列）
        assertEquals(3, result.size(), "workload 数量应与 Deployment+StatefulSet 一致");

        ServiceRuntimeEntity a = find(result, "svc-a");
        assertNotNull(a.getServiceSnapshot(), "有同名 Service 的项应带 service 快照");
        assertEquals("http", a.getTargetPort(), "具名端口必须原样保留字符串形态");
        assertEquals(8080, a.getPort());
        assertEquals(1, a.getReplicas());

        ServiceRuntimeEntity b = find(result, "svc-b");
        assertNull(b.getServiceSnapshot(), "没有同名 Service 的 workload 仍要入列，service 为空");
        assertEquals(0, b.getReplicas());

        ServiceRuntimeEntity c = find(result, "sts-c");
        assertNotNull(c.getDeploymentSnapshot(), "StatefulSet 的 workload 也要有快照");
        assertEquals(2, c.getReplicas());
    }

    private ServiceRuntimeEntity find(List<ServiceRuntimeEntity> list, String name) {
        return list.stream().filter(e -> name.equals(e.getName())).findFirst().orElseThrow();
    }

    private static Map<String, Object> snapshotOf(Object k8sObject) {
        Map<String, Object> map = new HashMap<>();
        String name = k8sObject instanceof Deployment d ? d.getMetadata().getName()
                : k8sObject instanceof StatefulSet s ? s.getMetadata().getName()
                : k8sObject instanceof Service s ? s.getMetadata().getName() : "?";
        map.put("metadata", Map.of("name", name, "namespace", NS, "creationTimestamp", "2026-10-13T15:22:33Z"));
        return map;
    }

    private static Deployment deployment(String name, int replicas) {
        return new DeploymentBuilder()
                .withNewMetadata().withName(name).withNamespace(NS)
                .withCreationTimestamp("2026-10-13T15:22:33Z").endMetadata()
                .withNewSpec().withReplicas(replicas).endSpec()
                .build();
    }

    private static StatefulSet statefulSet(String name, int replicas) {
        return new StatefulSetBuilder()
                .withNewMetadata().withName(name).withNamespace(NS)
                .withCreationTimestamp("2026-10-13T15:20:00Z").endMetadata()
                .withNewSpec().withReplicas(replicas).endSpec()
                .build();
    }

    private static Service serviceWithNamedPort(String name, String portName, int port) {
        return new ServiceBuilder()
                .withNewMetadata().withName(name).withNamespace(NS)
                .withCreationTimestamp("2026-10-13T15:22:33Z").endMetadata()
                .withNewSpec()
                .addNewPort().withName(portName).withPort(port).withTargetPort(new IntOrString(portName)).endPort()
                .endSpec()
                .build();
    }
}
