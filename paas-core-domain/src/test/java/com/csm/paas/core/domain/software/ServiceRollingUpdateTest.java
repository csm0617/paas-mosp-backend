package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ServiceImageVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/rollingUpdate`（厂商 `ServiceController.rollingUpdate:2599-2644`）。
 * <p>规格见 `docs/findings/122`。本层锁住"传了什么给端口"、错误映射，以及**顺序**：
 * 厂商是 patch 成功**之后**才 `updateSoftwareTimeByService`（失败时不动库）。
 */
class ServiceRollingUpdateTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        return impl;
    }

    private static ServiceImageVO form(Integer type) {
        return ServiceImageVO.builder().name("nps-svc").type(type).version("nginx:1.27").build();
    }

    @Test
    void patchesDeploymentThenTouchesRelatedSoftware() {
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of("sw1", "sw2"));

        assertTrue(service().updateServiceImageRollingUpdate("ns", form(1)));

        verify(port).patchDeploymentRollingUpdate(eq("ns"), eq("nps-svc"), eq(1), eq("nginx:1.27"), anyLong());
        verify(softwareRepository).updateSoftwareUpdatedAt(eq("sw1"), anyLong());
        verify(softwareRepository).updateSoftwareUpdatedAt(eq("sw2"), anyLong());
    }

    @Test
    void otherTypeStillPatchesButStrategyIsDecidedInInfrastructure() {
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of());

        assertTrue(service().updateServiceImageRollingUpdate("ns", form(99)));

        verify(port).patchDeploymentRollingUpdate(eq("ns"), eq("nps-svc"), eq(99), eq("nginx:1.27"), anyLong());
    }

    @Test
    void missingDeploymentYieldsServiceNameNotFoundAndNoDbWrite() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> impl.updateServiceImageRollingUpdate("ns", form(1)));
        assertEquals("service.not.found.update.image.version", e.getI18nKey());
        assertEquals("nps-svc", e.getArgs()[0], "文案里的 {0} 是服务名");
        verify(port, never()).patchDeploymentRollingUpdate(anyString(), anyString(), any(), any(), anyLong());
        verify(softwareRepository, never()).updateSoftwareUpdatedAt(anyString(), anyLong());
    }

    @Test
    void patchFailureYieldsApiFailedKeyAndNoDbWrite() {
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).patchDeploymentRollingUpdate(anyString(), anyString(), any(), any(), anyLong());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceImageRollingUpdate("ns", form(2)));
        assertEquals("k8s.call.api.failed.to.change.image.version", e.getI18nKey());
        verify(softwareRepository, never()).querySoftwareIdsByAppName(anyString());
        verify(softwareRepository, never()).updateSoftwareUpdatedAt(anyString(), anyLong());
    }

    @Test
    void indexOutOfBoundsFromEmptyContainersPropagates() {
        // 厂商没兜 `containers.get(0)` ⇒ 500 系统错误；我们也不兜
        Mockito.doThrow(new IndexOutOfBoundsException("Index 0 out of bounds"))
                .when(port).patchDeploymentRollingUpdate(anyString(), anyString(), any(), any(), anyLong());

        assertThrows(IndexOutOfBoundsException.class,
                () -> service().updateServiceImageRollingUpdate("ns", form(1)));
    }
}
