package com.csm.paas.core.infrastructure.config;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 具名端口回归。
 * <p>厂商后端内嵌 io.kubernetes:client-java 18.0.1 的模型把 {@code ServicePort.targetPort} 与
 * {@code TCPSocketAction.port} 声明成 Integer，只要集群里出现 {@code targetPort: http} 或
 * {@code tcpSocket.port: http} 就抛
 * {@code Cannot deserialize Integer from String}，导致网络管理页白屏。
 * <p>本测试锁定 fabric8 7.9.0 的模型不会重蹈覆辙。
 */
class NamedPortDeserializationTest {

    @Test
    void serviceWithNamedTargetPortMustDeserialize() {
        String json = """
                {
                  "apiVersion": "v1",
                  "kind": "Service",
                  "metadata": {"name": "gaop-be", "namespace": "gaop-namespace"},
                  "spec": {
                    "ports": [{"name": "http", "port": 8080, "targetPort": "http", "protocol": "TCP"}]
                  }
                }
                """;

        Service service = Serialization.unmarshal(json, Service.class);

        assertNotNull(service.getSpec().getPorts().get(0).getTargetPort());
        assertEquals("http", service.getSpec().getPorts().get(0).getTargetPort().getStrVal());
    }

    @Test
    void deploymentWithNamedTcpSocketProbeMustDeserialize() {
        String json = """
                {
                  "apiVersion": "apps/v1",
                  "kind": "Deployment",
                  "metadata": {"name": "gaop-be", "namespace": "gaop-namespace"},
                  "spec": {
                    "template": {
                      "spec": {
                        "containers": [{
                          "name": "gaop-be",
                          "image": "example/gaop-be:1.3.4",
                          "livenessProbe": {"tcpSocket": {"port": "http"}}
                        }]
                      }
                    }
                  }
                }
                """;

        Deployment deployment = Serialization.unmarshal(json, Deployment.class);

        var tcpSocket = deployment.getSpec().getTemplate().getSpec()
                .getContainers().get(0).getLivenessProbe().getTcpSocket();
        assertNotNull(tcpSocket);
        assertEquals("http", tcpSocket.getPort().getStrVal());
    }
}
