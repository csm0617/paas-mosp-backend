package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/service/baseEvent`（厂商 `ServiceController.getBaseServiceEvent:3377-3421`）。
 * <p>规格见 `docs/findings/125`。锁住：命名空间写死 `kube-system`、`kube-proxy` 用 `k8s-app` 而其它用 `name`、
 * ReplicaSet 名直接用 `<baseServiceName>-`、时间**降序且空值排最后**（厂商无空值保护 ⇒ 会 NPE）、
 * 以及三个错误键的分工。
 */
class ServiceBaseEventTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> event(String name, String timestamp) {
        Map<String, Object> e = new HashMap<>();
        e.put("metadata", Map.of("name", name));
        if (timestamp != null) {
            e.put("lastTimestamp", timestamp);
        }
        return e;
    }

    private void pods(String... names) {
        when(port.listPodsByLabel(eq("kube-system"), anyString(), anyString())).thenReturn(
                java.util.Arrays.stream(names)
                        .map(n -> ServicePodRefVO.builder().name(n).build())
                        .toList());
    }

    @Test
    void kubeProxyUsesK8sAppLabelOthersUseName() {
        pods("kube-proxy-abc");
        when(port.listEvents(eq("kube-system"), eq("kube-proxy-"), eq("ReplicaSet"))).thenReturn(List.of());
        when(port.listEvents(eq("kube-system"), eq("kube-proxy-abc"), eq("Pod"))).thenReturn(List.of());

        service().queryBaseServiceEvents("kube-proxy");
        verify(port).listPodsByLabel("kube-system", "k8s-app", "kube-proxy");

        service().queryBaseServiceEvents("kube-keepalived-vip");
        verify(port).listPodsByLabel("kube-system", "name", "kube-keepalived-vip");
    }

    @Test
    void replicaSetNameIsBaseServiceNameWithDash() {
        pods("kube-proxy-abc");
        when(port.listEvents(eq("kube-system"), eq("kube-proxy-"), eq("ReplicaSet"))).thenReturn(List.of());
        when(port.listEvents(eq("kube-system"), eq("kube-proxy-abc"), eq("Pod"))).thenReturn(List.of());

        service().queryBaseServiceEvents("kube-proxy");

        // 与 /event 不同：**不**从 generateName 推，直接 <name>-
        verify(port).listEvents("kube-system", "kube-proxy-", "ReplicaSet");
        verify(port).listEvents("kube-system", "kube-proxy-abc", "Pod");
    }

    @Test
    void sortsDescendingWithNullsLast() {
        pods("p1");
        when(port.listEvents(eq("kube-system"), eq("svc-"), eq("ReplicaSet"))).thenReturn(List.of(
                event("old", "2026-10-14T01:00:00Z"), event("no-ts", null)));
        when(port.listEvents(eq("kube-system"), eq("p1"), eq("Pod"))).thenReturn(List.of(
                event("new", "2026-10-14T03:00:00Z")));

        var result = service().queryBaseServiceEvents("svc");

        assertEquals(List.of("new", "old", "no-ts"), result.stream()
                .map(e -> ((Map<?, ?>) e.get("metadata")).get("name")).toList(),
                "时间降序，且缺 lastTimestamp 的排最后（厂商此处会 NPE，见 deviations B19）");
    }

    @Test
    void emptyPodListStillQueriesReplicaSetEvents() {
        pods();
        when(port.listEvents(eq("kube-system"), eq("svc-"), eq("ReplicaSet"))).thenReturn(List.of(event("rs", "2026-10-14T01:00:00Z")));

        var result = service().queryBaseServiceEvents("svc");

        assertEquals(1, result.size(), "厂商没有\"没有 pod 就提前返回\"的短路，照样查 ReplicaSet 事件");
    }

    @Test
    void threeDistinctErrorKeys() {
        when(port.listPodsByLabel(anyString(), anyString(), anyString()))
                .thenThrow(new K8sApiException("boom"));
        assertEquals("service.get.basic.service.container.info.failed",
                assertThrows(WrongArgumentException.class, () -> service().queryBaseServiceEvents("svc")).getI18nKey());

        pods("p1");
        when(port.listEvents(eq("kube-system"), eq("svc-"), eq("ReplicaSet")))
                .thenThrow(new K8sApiException("boom"));
        assertEquals("service.get.container.event.info.failed",
                assertThrows(WrongArgumentException.class, () -> service().queryBaseServiceEvents("svc")).getI18nKey());

        when(port.listEvents(eq("kube-system"), eq("svc-"), eq("ReplicaSet"))).thenReturn(List.of());
        when(port.listEvents(eq("kube-system"), eq("p1"), eq("Pod")))
                .thenThrow(new K8sApiException("boom"));
        assertEquals("service.get.container.pod.event.info.failed",
                assertThrows(WrongArgumentException.class, () -> service().queryBaseServiceEvents("svc")).getI18nKey());
    }

    @Test
    void allEventsEmptyYieldsEmptyList() {
        pods("p1");
        when(port.listEvents(anyString(), anyString(), anyString())).thenReturn(List.of());
        assertTrue(service().queryBaseServiceEvents("svc").isEmpty());
    }
}
