package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.SystemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * `PUT /v1/service/updateName` 的编排（厂商 `ServiceController:1536-1561`）：
 * 先 Deployment，**任意异常**回退 StatefulSet；回退也失败 → `service.modify.name.failed`；
 * 两条成功路径都要落 i18n 自动名。
 */
class ServiceUpdateNameTest {

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
    void patchesDeploymentThenWritesI18n() {
        assertTrue(service().updateServiceName("ns", "svc-a", "新名字", "NewName", "Nouveau"));

        verify(port).patchDeploymentServiceLabel("ns", "svc-a", "新名字");
        verify(port, never()).patchStatefulSetServiceLabel(any(), any(), any());
        verify(settingRepository).upsertValue(eq("i18n.service.en_US.svc-a"), eq("NewName"), any());
        verify(settingRepository).upsertValue(eq("i18n.service.fr_FR.svc-a"), eq("Nouveau"), any());
    }

    /** Deployment 失败 → 回退 StatefulSet，仍然成功。 */
    @Test
    void fallsBackToStatefulSetOnAnyDeploymentFailure() {
        doThrow(new RuntimeException("deployment patch failed"))
                .when(port).patchDeploymentServiceLabel(any(), any(), any());

        assertTrue(service().updateServiceName("ns", "svc-a", "新名字", "NewName", "Nouveau"));

        var order = inOrder(port);
        order.verify(port).patchDeploymentServiceLabel("ns", "svc-a", "新名字");
        order.verify(port).patchStatefulSetServiceLabel("ns", "svc-a", "新名字");
    }

    /** 回退也失败 → `service.modify.name.failed`，且**不写 i18n**。 */
    @Test
    void throwsVendorErrorWhenBothFail() {
        doThrow(new RuntimeException("deployment")).when(port).patchDeploymentServiceLabel(any(), any(), any());
        doThrow(new RuntimeException("statefulset")).when(port).patchStatefulSetServiceLabel(any(), any(), any());

        SystemException e = assertThrows(SystemException.class,
                () -> service().updateServiceName("ns", "svc-a", "新名字", "NewName", "Nouveau"));

        assertEquals("service.modify.name.failed", e.getI18nKey());
        verify(settingRepository, never()).upsertValue(any(), any(), any());
    }
}
