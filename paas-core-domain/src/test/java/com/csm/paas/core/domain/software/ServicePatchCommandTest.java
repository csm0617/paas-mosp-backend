package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/patchCommand/{serviceName}`（厂商 `ServiceController:992-1010`）。
 * <p>两个要点：**读不到 Deployment 直接 400，没有 StatefulSet 回退**；厂商返回 `Response.ok()`
 * **不带 body** ⇒ 响应 `data` 为 null。
 */
class ServicePatchCommandTest {

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
    void passesCommandAndArgsThrough() {
        when(port.patchDeploymentCommand("ns", "svc", List.of("/bin/sh", "-c"), List.of("echo hi")))
                .thenReturn(true);

        assertTrue(service().updateServiceCommand("ns", "svc", List.of("/bin/sh", "-c"), List.of("echo hi")));
    }

    @Test
    void missingDeploymentThrowsVendorKeyWithNoFallback() {
        when(port.patchDeploymentCommand(any(), any(), anyList(), anyList())).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceCommand("ns", "no-such", List.of("x"), List.of()));
        assertEquals("service.service.name.not.found", e.getI18nKey());
        verify(port, never()).statefulSetHostname(any(), any());
    }
}
