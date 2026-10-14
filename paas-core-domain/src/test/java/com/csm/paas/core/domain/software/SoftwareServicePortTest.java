package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ServicePortItemVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/software/servicePort`（厂商 `SoftwareService.softwareServicePort:2836-2876`）。
 * <p>规格见 `docs/findings/136`。重点：**web 入口标记的保留规则**（只有旧入口的 port 还在新列表里才继续标 true）、
 * 先删后插、`path_name = serviceName + "-" + port`、**不写 allow**、以及最后通知网关。
 */
class SoftwareServicePortTest {

    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IGatewayRefreshPort gatewayRefreshPort = mock(IGatewayRefreshPort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "gatewayRefreshPort", gatewayRefreshPort);
        return impl;
    }

    private static ServicePortItemVO item(String serviceName, Integer port) {
        return ServicePortItemVO.builder().serviceName(serviceName).containerPort(port).build();
    }

    @Test
    void writesRowsAndKeepsWebEntryOnlyWhenItsPortSurvives() {
        when(softwareRepository.querySoftwareIdsByAppName("svc")).thenReturn(List.of("sw1"));
        when(softwareRepository.queryWebEntryPort("sw1", "svc")).thenReturn(Optional.of(8080));

        assertTrue(service().updateSoftwareServicePorts(List.of(item("svc", 8080), item("svc", 9090))));

        verify(softwareRepository).deleteServicePorts("sw1", "svc");
        verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(8080), eq("svc-8080"), eq(true), anyLong());
        verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(9090), eq("svc-9090"), eq(false), anyLong());
        verify(gatewayRefreshPort).refresh();
    }

    @Test
    void webEntryFlagIsDroppedWhenItsPortIsGone() {
        when(softwareRepository.querySoftwareIdsByAppName("svc")).thenReturn(List.of("sw1"));
        when(softwareRepository.queryWebEntryPort("sw1", "svc")).thenReturn(Optional.of(8080));

        service().updateSoftwareServicePorts(List.of(item("svc", 9090)));

        verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(9090), eq("svc-9090"), eq(false), anyLong());
    }

    @Test
    void noWebEntryBeforeMeansAllRowsFalse() {
        when(softwareRepository.querySoftwareIdsByAppName("svc")).thenReturn(List.of("sw1"));
        when(softwareRepository.queryWebEntryPort("sw1", "svc")).thenReturn(Optional.empty());

        service().updateSoftwareServicePorts(List.of(item("svc", 8080)));

        verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(8080), eq("svc-8080"), eq(false), anyLong());
    }

    @Test
    void everySoftwareSharingTheAppNameGetsRows() {
        when(softwareRepository.querySoftwareIdsByAppName("svc")).thenReturn(List.of("sw1", "sw2"));
        when(softwareRepository.queryWebEntryPort(anyString(), anyString())).thenReturn(Optional.empty());

        service().updateSoftwareServicePorts(List.of(item("svc", 8080)));

        verify(softwareRepository).deleteServicePorts("sw1", "svc");
        verify(softwareRepository).deleteServicePorts("sw2", "svc");
        verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(8080), anyString(), eq(false), anyLong());
        verify(softwareRepository).insertServicePortRow(eq("sw2"), eq("svc"), eq(8080), anyString(), eq(false), anyLong());
    }

    @Test
    void unknownServiceNameIsANoOpButStillTrue() {
        when(softwareRepository.querySoftwareIdsByAppName("nope")).thenReturn(List.of());

        assertTrue(service().updateSoftwareServicePorts(List.of(item("nope", 8080))));

        verify(softwareRepository, never()).deleteServicePorts(anyString(), anyString());
        verify(softwareRepository, never()).insertServicePortRow(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyLong());
        verify(gatewayRefreshPort, never()).refresh();
    }

    @Test
    void emptyFormThrowsLikeTheVendor() {
        // 厂商 `form.get(0)` ⇒ IndexOutOfBounds ⇒ 500（照抄，不额外兜）
        assertThrows(IndexOutOfBoundsException.class,
                () -> service().updateSoftwareServicePorts(List.of()));
    }

    @Test
    void rowsAreDeletedBeforeInserting() {
        when(softwareRepository.querySoftwareIdsByAppName("svc")).thenReturn(List.of("sw1"));
        when(softwareRepository.queryWebEntryPort("sw1", "svc")).thenReturn(Optional.empty());

        service().updateSoftwareServicePorts(List.of(item("svc", 8080)));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(softwareRepository);
        order.verify(softwareRepository).queryWebEntryPort("sw1", "svc");
        order.verify(softwareRepository).deleteServicePorts("sw1", "svc");
        order.verify(softwareRepository).insertServicePortRow(eq("sw1"), eq("svc"), eq(8080), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyLong());
    }
}
