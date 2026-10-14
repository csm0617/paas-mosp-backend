package com.csm.paas.core.infrastructure.gateway;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复刻厂商 `otherPlugin` 的标签/命名空间选择器语义（`ServiceController:4065-4066`）：
 * `metadata.namespace!=gaop-namespace` 与
 * `!system-network,!log,app.kubernetes.io/instance!=prometheus,app!=harbor,tier!=control-plane`。
 *
 * <p>关键语义（k8s 规则，容易被"直觉"写错）：
 * **`k!=v` 对"标签不存在"也成立**，而 `!k` 要求键不存在。所以"没有任何这些标签"的工作负载**会被保留**。
 */
class OtherPluginSelectorTest {

    private static Deployment deploy(String namespace, Map<String, String> labels) {
        return new DeploymentBuilder().withNewMetadata()
                .withNamespace(namespace).withName("x").withLabels(labels).endMetadata().build();
    }

    @Test
    void keepsWorkloadsWithoutAnyOfThoseLabels() {
        assertTrue(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("default", Map.of())),
                "空标签集：!k 成立、k!=v 也成立 ⇒ 保留");
        assertTrue(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("kube-system", Map.of("app", "coredns"))));
    }

    @Test
    void excludesGaopNamespace() {
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("gaop-namespace", Map.of())));
    }

    @Test
    void excludesForbiddenLabelKeysAndValues() {
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("system-network", "x"))),
                "!system-network ⇒ 有该键就排除");
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("log", "x"))));
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d",
                Map.of("app.kubernetes.io/instance", "prometheus"))));
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("app", "harbor"))));
        assertFalse(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("tier", "control-plane"))));
    }

    /** `!=` 只排除"等于该值"的情况：同键不同值要保留。 */
    @Test
    void keepsSameKeyWithDifferentValue() {
        assertTrue(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("app", "nginx"))));
        assertTrue(ServiceRuntimeGateway.matchesOtherPluginSelector(deploy("d", Map.of("tier", "worker"))));
    }
}
