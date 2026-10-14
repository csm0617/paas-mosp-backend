package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/hostName` 的编排（厂商 `ServiceController:2693-2712`）。
 * <p>两条要点：**两边都空白 → 静默 no-op**；**只有"读不到 Deployment"才回退 StatefulSet**（不是 hostname 为空就回退）。
 */
class ServiceHostNameTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final IServiceAuthRepository authRepository = mock(IServiceAuthRepository.class);
    private final ISettingRepository settingRepository = mock(ISettingRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceAuthRepository", authRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "i18nRepository", settingRepository);
        return impl;
    }

    @Test
    void blankToBlankIsSilentNoop() {
        when(port.deploymentExists("ns", "svc")).thenReturn(true);
        when(port.deploymentHostname("ns", "svc")).thenReturn(null);

        assertTrue(service().updateServiceHostname("ns", "svc", ""));
        verify(port, never()).patchDeploymentHostname(any(), any(), any());
    }

    /** Deployment 存在但没 hostname，请求要设 → 必须走 Deployment，**不能**回退 StatefulSet。 */
    @Test
    void setsHostnameOnDeploymentEvenWhenItWasAbsent() {
        when(port.deploymentExists("ns", "svc")).thenReturn(true);
        when(port.deploymentHostname("ns", "svc")).thenReturn(null);

        assertTrue(service().updateServiceHostname("ns", "svc", "myhost"));

        verify(port).patchDeploymentHostname("ns", "svc", "myhost");
        verify(port, never()).statefulSetHostname(any(), any());
    }

    @Test
    void fallsBackToStatefulSetOnlyWhenDeploymentIsMissing() {
        when(port.deploymentExists("ns", "svc")).thenReturn(false);
        when(port.statefulSetHostname("ns", "svc")).thenReturn("old");

        assertTrue(service().updateServiceHostname("ns", "svc", "new"));

        verify(port).patchStatefulSetHostname("ns", "svc", "new");
    }

    @Test
    void neitherExistsThrowsVendorKeyAs400() {
        when(port.deploymentExists("ns", "svc")).thenReturn(false);
        when(port.statefulSetHostname("ns", "svc")).thenReturn(null);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceHostname("ns", "svc", "x"));
        assertEquals("service.not.found.update.host.pod", e.getI18nKey(), "实测厂商文案（400）");
    }
}
