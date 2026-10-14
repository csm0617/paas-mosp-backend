package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/pub/nodePortUsed`（厂商 `UserService.getNodePortUsedList:2120-2240`）。
 * <p>规格见 `docs/findings/141`。锁住三段来源的形状：① http/https 注解两条**同名"框架内部"**；
 * ② tcp/udp ConfigMap 的 `key=nodePort`、`value=<proto>/<serviceName>`；③ Service 条目名是
 * `<标签 name 或 app>:<service port>` 而 port 是 **nodePort**；以及 `port` 过滤与"读 Service 失败即返回"。
 */
class NodePortUsedTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> serviceSnapshot(String name, Map<String, Object> labels,
                                                       Integer servicePort, Integer nodePort) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("labels", labels);
        Map<String, Object> p = new HashMap<>();
        p.put("port", servicePort);
        p.put("nodePort", nodePort);
        Map<String, Object> spec = new HashMap<>();
        spec.put("type", "NodePort");
        spec.put("ports", List.of(p));
        Map<String, Object> svc = new HashMap<>();
        svc.put("metadata", metadata);
        svc.put("spec", spec);
        return svc;
    }

    @Test
    void httpAndHttpsAnnotationsProduceTwoEntriesWithTheSameName() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of("ing-a"));
        when(port.readDeploymentAnnotations("ns", "ing-a")).thenReturn(
                Map.of("httpPort", "30080", "httpsPort", "30443"));
        when(port.readConfigMapData(anyString(), anyString())).thenReturn(null);
        when(port.listNodePortServices("ns")).thenReturn(List.of());

        var result = service().queryNodePortUsed("ns", null);

        assertEquals(2, result.size());
        assertEquals("框架内部", result.get(0).getName());
        assertEquals(30080, result.get(0).getPort());
        assertEquals("框架内部", result.get(1).getName(), "两条**同名**（厂商如此）");
        assertEquals(30443, result.get(1).getPort());
    }

    @Test
    void tcpAndUdpConfigMapsUseKeyAsPortAndValueServiceName() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of("ing-a"));
        when(port.readDeploymentAnnotations("ns", "ing-a")).thenReturn(
                Map.of("httpPort", "30080", "httpsPort", "30443"));
        // 标签值 "ing-a" 按 '-' 拆开取第 2 段 = "a" ⇒ tcp-services-a / udp-services-a
        when(port.readConfigMapData("ns", "tcp-services-a")).thenReturn(Map.of("30100", "tcp/svc-tcp"));
        when(port.readConfigMapData("ns", "udp-services-a")).thenReturn(Map.of("30101", "udp/svc-udp"));
        when(port.listNodePortServices("ns")).thenReturn(List.of());

        var result = service().queryNodePortUsed("ns", null);

        assertEquals(4, result.size());
        assertEquals(30100, result.get(2).getPort());
        assertEquals("svc-tcp", result.get(2).getName());
        assertEquals(30101, result.get(3).getPort());
        assertEquals("svc-udp", result.get(3).getName());
    }

    @Test
    void serviceEntriesUseLabelNameAndServicePortInTheName() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of());
        when(port.listNodePortServices("ns")).thenReturn(List.of(
                serviceSnapshot("probe-np", Map.of("name", "probe-np"), 8080, 31000)));

        var result = service().queryNodePortUsed("ns", null);

        assertEquals(1, result.size());
        assertEquals("probe-np:8080", result.get(0).getName(), "条目名用 service port，不是 nodePort");
        assertEquals(31000, result.get(0).getPort());
    }

    @Test
    void serviceEntryFallsBackToAppLabel() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of());
        when(port.listNodePortServices("ns")).thenReturn(List.of(
                serviceSnapshot("s", Map.of("app", "my-app"), 80, 32000)));

        var result = service().queryNodePortUsed("ns", null);

        assertEquals("my-app:80", result.get(0).getName());
    }

    @Test
    void portFilterKeepsOnlyMatchingEntries() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of());
        when(port.listNodePortServices("ns")).thenReturn(List.of(
                serviceSnapshot("a", Map.of("name", "a"), 8080, 31000),
                serviceSnapshot("b", Map.of("name", "b"), 8081, 31001)));

        var result = service().queryNodePortUsed("ns", 31001);

        assertEquals(1, result.size());
        assertEquals("b:8081", result.get(0).getName());
    }

    @Test
    void missingAnnotationThrowsLikeTheVendor() {
        when(port.listIngressLbValues("ns")).thenReturn(List.of("ing-a"));
        when(port.readDeploymentAnnotations("ns", "ing-a")).thenReturn(Map.of("httpPort", "30080"));

        // 厂商 `Integer.valueOf(null)` ⇒ NumberFormatException（照抄，不额外兜）
        assertThrows(NumberFormatException.class, () -> service().queryNodePortUsed("ns", null));
    }
}
