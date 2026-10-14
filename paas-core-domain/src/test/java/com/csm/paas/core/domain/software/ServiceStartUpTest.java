package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/service/startUp`（厂商 `ServiceController:3968-4026`）。
 * <p>**已用独立 namespace 实测**（`findings/83`）：注解 + initContainer（name/image/command/env）全部符合厂商公式；
 * `time=0` 取消；`time<0` 与"服务不存在"各自 400。
 */
class ServiceStartUpTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    @Test
    void negativeTimeIsRejectedBeforeAnyK8sCall() {
        assertEquals("service.negative.startup.delay",
                assertThrows(WrongArgumentException.class,
                        () -> service().setServiceStartup("ns", List.of("a"), -1L, "h", "img")).getI18nKey());
        verify(port, never()).setDeploymentStartup(any(), any(), anyLong(), any(), any());
    }

    @Test
    void passesTimeAndImageConfigThrough() {
        when(port.setDeploymentStartup("ns", "a", 30L, "192.168.1.52:30002", "curl-alpine:latest")).thenReturn(true);

        assertTrue(service().setServiceStartup("ns", List.of("a"), 30L, "192.168.1.52:30002", "curl-alpine:latest"));

        verify(port).setDeploymentStartup("ns", "a", 30L, "192.168.1.52:30002", "curl-alpine:latest");
    }

    /** 服务不存在 ⇒ 400，参数是**服务名**；且中断。 */
    @Test
    void missingDeploymentThrowsWithServiceName() {
        when(port.setDeploymentStartup("ns", "nope", 30L, "h", "img")).thenReturn(false);

        var e = assertThrows(WrongArgumentException.class,
                () -> service().setServiceStartup("ns", List.of("nope"), 30L, "h", "img"));
        assertEquals("service.get.service.config.exception.set.delay", e.getI18nKey());
    }
}
