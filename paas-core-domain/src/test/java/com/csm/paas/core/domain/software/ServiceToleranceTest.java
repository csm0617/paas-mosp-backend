package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.TolerationVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * `POST /v1/service/tolerance?serviceName&type`（厂商 `ServiceController.allowMasterTolerance:3425-3475`）。
 * <p>规格见 `docs/findings/124`。锁住三态：0=关（空则不打 patch）、1=开（固定那一条）、其它 ⇒ `data:false`；
 * 以及那个**拼重了的 i18n 键**（消息包查不到 ⇒ 回退成键本身，与厂商响应一致）。
 */
class ServiceToleranceTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        return impl;
    }

    @Test
    void typeOneWritesTheFixedMasterToleration() {
        assertTrue(service().allowMasterTolerance("ns", "nps-svc", 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TolerationVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).patchDeploymentTolerations(eq("ns"), eq("nps-svc"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("node-role.kubernetes.io/master", captor.getValue().get(0).getKey());
        assertEquals("Exists", captor.getValue().get(0).getOperator());
        assertEquals("NoSchedule", captor.getValue().get(0).getEffect());
    }

    @Test
    void typeZeroWithNoTolerationsReturnsTrueWithoutPatching() {
        when(port.readDeploymentTolerations("ns", "nps-svc")).thenReturn(List.of());

        assertTrue(service().allowMasterTolerance("ns", "nps-svc", 0));
        verify(port, never()).patchDeploymentTolerations(anyString(), anyString(), any());
    }

    @Test
    void typeZeroWithTolerationsClearsThem() {
        when(port.readDeploymentTolerations("ns", "nps-svc")).thenReturn(List.of(
                TolerationVO.builder().key("k").operator("Exists").effect("NoSchedule").build()));

        assertTrue(service().allowMasterTolerance("ns", "nps-svc", 0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TolerationVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).patchDeploymentTolerations(eq("ns"), eq("nps-svc"), captor.capture());
        assertTrue(captor.getValue().isEmpty(), "整份置空");
    }

    @Test
    void otherTypeReturnsFalseWithoutPatching() {
        assertFalse(service().allowMasterTolerance("ns", "nps-svc", 9));
        assertFalse(service().allowMasterTolerance("ns", "nps-svc", null), "type 为 null 也走 false 分支");
        verify(port, never()).patchDeploymentTolerations(anyString(), anyString(), any());
    }

    @Test
    void missingDeploymentYieldsTheDoubledI18nKey() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> impl.allowMasterTolerance("ns", "nps-svc", 1));
        assertEquals("service.not.found.update.taintservice.not.found.update.taint", e.getI18nKey(),
                "厂商源码里的键就是拼重了的字面量；消息包查不到 ⇒ 回退成键本身");
        assertEquals("nps-svc", e.getArgs()[0]);
    }

    @Test
    void patchFailureYieldsServiceUpdateFailed() {
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).patchDeploymentTolerations(anyString(), anyString(), any());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().allowMasterTolerance("ns", "nps-svc", 1));
        assertEquals("service.update.service.failed", e.getI18nKey());
        assertEquals("nps-svc", e.getArgs()[0]);
    }
}
