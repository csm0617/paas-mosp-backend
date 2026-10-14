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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/service/cancelStartUp`（厂商 `ServiceController:4030-4055`）。
 * <p>**已用独立 namespace 实测**（`findings/82`）：注解 `gaop/serviceInitTime` 与 `initContainers` 都被清掉；
 * 不存在的服务 → 400 且文案与厂商一致（只差服务名）。
 */
class ServiceCancelStartUpTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    @Test
    void callsPortForEachServiceAndReturnsTrue() {
        when(port.cancelDeploymentStartup("ns", "a")).thenReturn(true);
        when(port.cancelDeploymentStartup("ns", "b")).thenReturn(true);

        assertTrue(service().cancelServiceStartup("ns", List.of("a", "b")));

        verify(port).cancelDeploymentStartup("ns", "a");
        verify(port).cancelDeploymentStartup("ns", "b");
    }

    /** 读不到 Deployment ⇒ 400，参数是**服务名**；且**中断**（后面的服务不再处理）。 */
    @Test
    void missingDeploymentThrowsWithServiceNameAndStops() {
        when(port.cancelDeploymentStartup("ns", "a")).thenReturn(true);
        when(port.cancelDeploymentStartup("ns", "b")).thenReturn(false);

        var e = assertThrows(WrongArgumentException.class,
                () -> service().cancelServiceStartup("ns", List.of("a", "b")));

        assertEquals("service.get.service.config.exception.cancel.delay", e.getI18nKey());
        verify(port, never()).cancelDeploymentStartup(anyString(), org.mockito.ArgumentMatchers.eq("c"));
    }
}
