package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/service/baseComponent`（厂商 `ServiceController.getKubeSystemInfo:3218-3311`）。
 * <p>规格见 `docs/findings/128`。锁住：三个组件的固定顺序、`kube-proxy` 用 `k8s-app` 而另两个用 `name`、
 * **keepalived 没有 pod 时整条不出现**、以及"任何读失败都只记日志、端点不报错"。
 */
class ServiceBaseComponentTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> ds(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "DaemonSet");
        m.put("name", name);
        return m;
    }

    private static List<Map<String, Object>> pod(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        return List.of(m);
    }

    @Test
    void fixedOrderAndPerComponentLabelSelectors() {
        when(port.readDaemonSetSnapshot("kube-system", "weave-net")).thenReturn(ds("weave-net"));
        when(port.readDaemonSetSnapshot("kube-system", "kube-proxy")).thenReturn(ds("kube-proxy"));
        when(port.readDaemonSetSnapshot("kube-system", "kube-keepalived-vip")).thenReturn(ds("kav"));
        when(port.listPodsByLabelAndNode(eq("kube-system"), anyString(), anyString(), eq("node1")))
                .thenReturn(pod("p"));

        var result = service().queryBaseComponents("node1");

        assertEquals(3, result.size());
        assertEquals("weave-net", ((Map<?, ?>) result.get(0).get("daemonSet")).get("name"));
        assertEquals("kube-proxy", ((Map<?, ?>) result.get(1).get("daemonSet")).get("name"));
        verify(port).listPodsByLabelAndNode("kube-system", "name", "weave-net", "node1");
        verify(port).listPodsByLabelAndNode("kube-system", "k8s-app", "kube-proxy", "node1");
        verify(port).listPodsByLabelAndNode("kube-system", "name", "kube-keepalived-vip", "node1");
    }

    @Test
    void keepAlivedEntryIsOmittedWhenItsPodListIsEmpty() {
        when(port.readDaemonSetSnapshot(eq("kube-system"), anyString())).thenReturn(ds("x"));
        when(port.listPodsByLabelAndNode("kube-system", "name", "weave-net", "node1")).thenReturn(pod("w"));
        when(port.listPodsByLabelAndNode("kube-system", "k8s-app", "kube-proxy", "node1")).thenReturn(pod("k"));
        when(port.listPodsByLabelAndNode("kube-system", "name", "kube-keepalived-vip", "node1")).thenReturn(List.of());

        var result = service().queryBaseComponents("node1");

        assertEquals(2, result.size(), "keepalived 没有 pod ⇒ 整条不出现");
        assertTrue(result.stream().noneMatch(r -> "kav".equals(((Map<?, ?>) r.get("daemonSet") == null
                ? Map.of() : (Map<?, ?>) r.get("daemonSet")).get("name"))));
    }

    @Test
    void emptyPodListBecomesNullKey() {
        when(port.readDaemonSetSnapshot(eq("kube-system"), anyString())).thenReturn(null);
        when(port.listPodsByLabelAndNode(eq("kube-system"), anyString(), anyString(), eq("node1")))
                .thenReturn(List.of());

        var result = service().queryBaseComponents("node1");

        assertEquals(2, result.size());
        assertNull(result.get(0).get("daemonSet"), "读不到 DaemonSet ⇒ 值为 null（键仍在）");
        assertNull(result.get(0).get("v1PodList"), "pod 列表为空 ⇒ 厂商不 set，字段保持 null");
        assertTrue(result.get(0).containsKey("daemonSet"));
        assertTrue(result.get(0).containsKey("v1PodList"));
    }

    @Test
    void k8sFailuresAreSwallowedAndEntryStillAppears() {
        when(port.readDaemonSetSnapshot(anyString(), anyString()))
                .thenThrow(new K8sApiException("boom"));
        when(port.listPodsByLabelAndNode(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new K8sApiException("boom"));

        var result = service().queryBaseComponents("node1");

        assertEquals(2, result.size(), "weave-net 与 kube-proxy 照常出现（只记日志）；keepalived 因异常被省略");
        assertNull(result.get(0).get("daemonSet"));
        assertNull(result.get(0).get("v1PodList"));
    }
}
