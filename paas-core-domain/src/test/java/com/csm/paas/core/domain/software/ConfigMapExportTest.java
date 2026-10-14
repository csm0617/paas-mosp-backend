package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置组导出两端点（厂商 `SoftwareService.exportConfigMap:3453-3477` /
 * `exportTcpAndUdpConfigMap:3739-3774`）。规格见 `docs/findings/145`。
 */
class ConfigMapExportTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> cm(String name, String ingressLb) {
        Map<String, Object> labels = new HashMap<>();
        if (ingressLb != null) {
            labels.put("ingress-lb", ingressLb);
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("labels", labels);
        Map<String, Object> m = new HashMap<>();
        m.put("metadata", metadata);
        return m;
    }

    // ---------- /backup ----------

    @Test
    void namedListIsReadByName() {
        when(port.readConfigMaps("ns", List.of("a", "b"))).thenReturn(List.of(cm("a", null)));

        var result = service().exportConfigMaps("ns", List.of("a", "b"));

        assertEquals(1, result.size(), "读不到的 b 被端口跳过");
        verify(port, never()).listConfigMapsWithoutIngressLb(anyString());
    }

    @Test
    void emptyOrNullListExportsAllWithoutIngressLb() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("items", List.of(cm("x", null), cm("y", null)));
        when(port.listConfigMapsWithoutIngressLb("ns")).thenReturn(snapshot);

        assertEquals(2, service().exportConfigMaps("ns", List.of()).size());
        assertEquals(2, service().exportConfigMaps("ns", null).size());
    }

    // ---------- /listenBackup ----------

    @Test
    void listenBackupPicksTcpAndUdpByNameSuffix() {
        when(port.listConfigMapsWithIngressLb("ns")).thenReturn(List.of(
                cm("tcp-services-abc", "ing-abc"),
                cm("udp-services-abc", "ing-abc"),
                cm("unrelated", "ing-abc")));

        var info = service().exportListenConfigs("ns");

        assertEquals("tcp-services-abc", ((Map<?, ?>) info.getTcpListen().get("metadata")).get("name"));
        assertEquals("udp-services-abc", ((Map<?, ?>) info.getUdpListen().get("metadata")).get("name"));
        assertNull(info.getIngressList(), "ingressList 厂商从不赋值 ⇒ 恒为 null");
    }

    @Test
    void ingressLbTrueIsSkipped() {
        when(port.listConfigMapsWithIngressLb("ns")).thenReturn(List.of(
                cm("tcp-services-abc", "true"),
                cm("udp-services-abc", "true")));

        var info = service().exportListenConfigs("ns");

        assertNull(info.getTcpListen());
        assertNull(info.getUdpListen());
    }

    @Test
    void ingressLbWithoutDashIsSkippedNotFatal() {
        when(port.listConfigMapsWithIngressLb("ns")).thenReturn(List.of(cm("tcp-services-abc", "nodash")));

        var info = service().exportListenConfigs("ns");

        assertNull(info.getTcpListen(), "split(\"-\")[1] 抛异常被吞掉");
    }

    @Test
    void clusterFailureYieldsEmptyResult() {
        when(port.listConfigMapsWithIngressLb(anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));

        var info = service().exportListenConfigs("ns");

        assertNull(info.getTcpListen());
        assertNull(info.getUdpListen());
    }

    @Test
    void itemsAreTheSameSnapshotsAsFromTheCluster() {
        var snapshot = cm("tcp-services-abc", "ing-abc");
        when(port.listConfigMapsWithIngressLb("ns")).thenReturn(List.of(snapshot));

        assertSame(snapshot, service().exportListenConfigs("ns").getTcpListen());
    }

    @Test
    void noIngressLbConfigMapsMeansBothNull() {
        when(port.listConfigMapsWithIngressLb("ns")).thenReturn(new ArrayList<>());

        var info = service().exportListenConfigs("ns");

        assertTrue(info.getTcpListen() == null && info.getUdpListen() == null);
    }
}
