package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.HealthCheckVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/healthCheck`（厂商 `ServiceController.healthCheck:2658-2689`）。
 * <p>规格见 `docs/findings/121`。本层只锁"传什么给端口"与错误映射——
 * `type` → 探针形状的翻译在 infrastructure（`ServiceRuntimePort.toProbePatch`）。
 */
class ServiceHealthCheckTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        return impl;
    }

    private static HealthCheckVO.HealthSetVO set(Integer type) {
        return HealthCheckVO.HealthSetVO.builder()
                .type(type).path("/health").port(8080)
                .initialDelaySeconds(5).timeoutSeconds(3).periodSeconds(10)
                .successThreshold(1).failureThreshold(3).build();
    }

    @Test
    void passesBothProbesThrough() {
        assertTrue(service().updateHealthCheck("ns", HealthCheckVO.builder()
                .name("nps-svc").readiness(set(1)).liveness(set(2)).build()));

        var r = ArgumentCaptor.forClass(HealthCheckVO.HealthSetVO.class);
        var l = ArgumentCaptor.forClass(HealthCheckVO.HealthSetVO.class);
        verify(port).patchDeploymentProbes(eq("ns"), eq("nps-svc"), r.capture(), l.capture());
        assertEquals(1, r.getValue().getType());
        assertEquals("/health", r.getValue().getPath());
        assertEquals(8080, r.getValue().getPort());
        assertEquals(2, l.getValue().getType());
    }

    @Test
    void nullProbeMeansThatProbeIsUntouched() {
        assertTrue(service().updateHealthCheck("ns", HealthCheckVO.builder()
                .name("nps-svc").readiness(set(1)).build()));

        var r = ArgumentCaptor.forClass(HealthCheckVO.HealthSetVO.class);
        var l = ArgumentCaptor.forClass(HealthCheckVO.HealthSetVO.class);
        verify(port).patchDeploymentProbes(eq("ns"), eq("nps-svc"), r.capture(), l.capture());
        assertEquals(1, r.getValue().getType());
        assertNull(l.getValue(), "liveness 为 null ⇒ 端口那边不动这个探针");
    }

    @Test
    void missingDeploymentYieldsServiceNameNotFound() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> impl.updateHealthCheck("ns", HealthCheckVO.builder().name("nps-svc").build()));
        assertEquals("service.not.found.update.image.version", e.getI18nKey());
        assertEquals("nps-svc", e.getArgs()[0], "文案里的 {0} 是服务名");
        verify(port, never()).patchDeploymentProbes(anyString(), anyString(), any(), any());
    }

    @Test
    void k8sFailureDuringPatchYieldsApiFailedKey() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).patchDeploymentProbes(anyString(), anyString(), any(), any());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> impl.updateHealthCheck("ns", HealthCheckVO.builder().name("nps-svc").readiness(set(0)).build()));
        assertEquals("k8s.call.api.failed.to.change.image.version", e.getI18nKey());
    }

    @Test
    void indexOutOfBoundsPropagatesAsIsSoGlobalHandlerReturns500() {
        // 厂商对"容器列表为空"没兜 ⇒ 500 系统错误/code=-1；我们也不兜
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        Mockito.doThrow(new IndexOutOfBoundsException("Index 0 out of bounds"))
                .when(port).patchDeploymentProbes(anyString(), anyString(), any(), any());

        assertThrows(IndexOutOfBoundsException.class,
                () -> impl.updateHealthCheck("ns", HealthCheckVO.builder().name("nps-svc").readiness(set(1)).build()));
    }

    @Test
    void emptyFormStillCallsPort() {
        assertTrue(service().updateHealthCheck("ns", HealthCheckVO.builder().name("nps-svc").build()));
        verify(port).patchDeploymentProbes(eq("ns"), eq("nps-svc"), eq(null), eq(null));
    }
}
