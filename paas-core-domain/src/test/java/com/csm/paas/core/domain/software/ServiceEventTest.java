package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/service/event`（厂商 `ServiceController.getServiceEvent:2821-2859`）。
 * <p>规格见 `docs/findings/123`。锁住：`generateName` 去末位字符当 ReplicaSet 名、两类事件查询、
 * **排序是"空值最前 + 时间降序"**（厂商 `nullsLast(...).reversed()` 的实际效果）、
 * 没有匹配 pod 时返回空、以及 `generateName` 为 null 时跳过（我们的收紧 B18）。
 */
class ServiceEventTest {

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

    @Test
    void queriesReplicaSetThenEachPodAndSortsDescending() {
        when(port.listPodsByLabel("ns", "system/svcName", "nps-svc")).thenReturn(List.of(
                ServicePodRefVO.builder().name("nps-svc-abc-1").generateName("nps-svc-abc-").build()));
        when(port.listEvents("ns", "nps-svc-abc", "ReplicaSet")).thenReturn(List.of(
                event("rs-old", "2026-10-14T01:00:00Z"), event("rs-new", "2026-10-14T03:00:00Z")));
        when(port.listEvents("ns", "nps-svc-abc-1", "Pod")).thenReturn(List.of(
                event("pod-mid", "2026-10-14T02:00:00Z")));

        var result = service().queryServiceEvents("ns", "nps-svc");

        assertEquals(3, result.size());
        assertEquals(List.of("rs-new", "pod-mid", "rs-old"), result.stream()
                .map(e -> ((Map<?, ?>) e.get("metadata")).get("name")).toList());
        // generateName 去掉**末位字符**才是 ReplicaSet 名
        verify(port).listEvents("ns", "nps-svc-abc", "ReplicaSet");
        verify(port).listEvents("ns", "nps-svc-abc-1", "Pod");
    }

    @Test
    void nullTimestampSortsFirst() {
        when(port.listPodsByLabel("ns", "system/svcName", "nps-svc")).thenReturn(List.of(
                ServicePodRefVO.builder().name("p1").generateName("nps-svc-abc-").build()));
        when(port.listEvents("ns", "nps-svc-abc", "ReplicaSet")).thenReturn(List.of(
                event("with-ts", "2026-10-14T03:00:00Z"), event("no-ts", null)));

        var result = service().queryServiceEvents("ns", "nps-svc");

        assertEquals("no-ts", ((Map<?, ?>) result.get(0).get("metadata")).get("name"),
                "厂商 `nullsLast(...).reversed()` 把空值翻到了最前（照抄）");
        assertEquals("with-ts", ((Map<?, ?>) result.get(1).get("metadata")).get("name"));
    }

    @Test
    void noMatchingPodYieldsEmptyListWithoutQueryingEvents() {
        when(port.listPodsByLabel("ns", "system/svcName", "nps-svc")).thenReturn(List.of());

        assertTrue(service().queryServiceEvents("ns", "nps-svc").isEmpty());
        verify(port, never()).listEvents(anyString(), anyString(), anyString());
    }

    @Test
    void podWithoutGenerateNameIsSkipped() {
        when(port.listPodsByLabel("ns", "system/svcName", "nps-svc")).thenReturn(List.of(
                ServicePodRefVO.builder().name("bare-pod").generateName(null).build(),
                ServicePodRefVO.builder().name("nps-svc-abc-1").generateName("nps-svc-abc-").build()));
        when(port.listEvents("ns", "nps-svc-abc", "ReplicaSet")).thenReturn(List.of());

        var result = service().queryServiceEvents("ns", "nps-svc");

        assertTrue(result.isEmpty());
        verify(port, never()).listEvents(eq("ns"), eq("bare-pod"), eq("Pod"));
    }

    @Test
    void k8sFailureYieldsEventException() {
        when(port.listPodsByLabel("ns", "system/svcName", "nps-svc"))
                .thenThrow(new K8sApiException("boom"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().queryServiceEvents("ns", "nps-svc"));
        assertEquals("service.get.service.event.info.exception", e.getI18nKey());
    }
}
