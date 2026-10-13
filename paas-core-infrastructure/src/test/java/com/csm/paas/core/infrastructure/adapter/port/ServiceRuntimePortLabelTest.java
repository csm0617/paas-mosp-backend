package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 节点 label 聚合（厂商 `ServiceController.getLabels:964-990`）：
 * 把每个节点的 `metadata.labels` 按**键**聚合成"值集合"，产出 `{name: label键, labels: 去重值}`。
 * <p>两条容易读反的语义：`name` 是 label 的**键**（不是节点名）；`labels` 是该键的**去重值**（不是标签串）。
 */
class ServiceRuntimePortLabelTest {

    private final ServiceRuntimeGateway gateway = mock(ServiceRuntimeGateway.class);

    @Test
    void aggregatesValuesByLabelKeyAndDeduplicates() {
        when(gateway.listNodes()).thenReturn(List.of(
                node(Map.of("kubernetes.io/hostname", "worker01", "role", "worker")),
                node(Map.of("kubernetes.io/hostname", "worker02", "role", "worker")),
                node(Map.of("kubernetes.io/hostname", "master", "role", "master"))));

        Map<String, List<String>> labels = new ServiceRuntimePort(gateway).queryNodeLabels();

        assertEquals(2, labels.size());
        assertEquals(List.of("worker01", "worker02", "master"), labels.get("kubernetes.io/hostname"));
        assertEquals(List.of("worker", "master"), labels.get("role"), "同一个值只出现一次");
    }

    /** 节点没有 labels 时厂商是 `catch(NullPointerException) 忽略`，不报错。 */
    @Test
    void nodeWithoutLabelsIsIgnored() {
        Node noLabels = new NodeBuilder().withNewMetadata().withName("n1").endMetadata().build();
        when(gateway.listNodes()).thenReturn(List.of(noLabels));

        assertTrue(new ServiceRuntimePort(gateway).queryNodeLabels().isEmpty());
    }

    private static Node node(Map<String, String> labels) {
        return new NodeBuilder().withNewMetadata().withLabels(labels).endMetadata().build();
    }
}
