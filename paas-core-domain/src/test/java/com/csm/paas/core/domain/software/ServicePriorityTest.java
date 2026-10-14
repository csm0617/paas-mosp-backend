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
 * `POST /v1/service/priority`（厂商 `ServiceController:3029-3050`）。
 * <p>与 `hostName` 的关键区别：**只读 Deployment、没有 StatefulSet 回退**；`level` 空白设成**空串**。
 */
class ServicePriorityTest {

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
    void setsLevelOnDeployment() {
        when(port.deploymentExists("ns", "svc")).thenReturn(true);

        assertTrue(service().updateServicePriority("ns", "svc", "high-priority"));

        verify(port).patchDeploymentPriorityClassName("ns", "svc", "high-priority");
        verify(port, never()).statefulSetHostname(any(), any());
    }

    /** 空白 level → 设成**空串**（厂商 `setPriorityClassName("")`，不是 null）。 */
    @Test
    void blankLevelBecomesEmptyString() {
        when(port.deploymentExists("ns", "svc")).thenReturn(true);

        service().updateServicePriority("ns", "svc", "");

        verify(port).patchDeploymentPriorityClassName("ns", "svc", "");
    }

    @Test
    void missingDeploymentThrowsVendorKeyWithNoStatefulSetFallback() {
        when(port.deploymentExists("ns", "svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServicePriority("ns", "svc", "low-priority"));

        assertEquals("service.change.priority.failed", e.getI18nKey());
        verify(port, never()).patchDeploymentPriorityClassName(any(), any(), any());
    }
}
