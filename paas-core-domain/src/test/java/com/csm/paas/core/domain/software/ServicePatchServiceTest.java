package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.ContainerPortVO;
import com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO;
import com.csm.paas.core.domain.software.model.valobj.UpdatePortItemVO;
import com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/patchService` 的 **A 分支**（`type == "Service"`，厂商 `updateServiceDiscovery:1017-1087`）。
 *
 * <p>规格见 `docs/findings/118`。这里锁住三件最容易写错的事：
 * ① ports 由**容器端口**推导（不是入参 `portList`）；② `type` 写死 `ClusterIP`；
 * ③ 错误映射——Service 不存在 → 400 `k8s.call.api.failed`，其余 → 400 `service.update.service.discovery.failed`
 * （厂商内层那个 500 的 `SystemException` 被外层 `catch (Exception)` 吞掉了）。
 */
class ServicePatchServiceTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static UpdateServiceDiscoveryVO form(String type) {
        return UpdateServiceDiscoveryVO.builder()
                .type(type).serviceName("nps-svc")
                // 入参的 portList 在 A 分支**完全不被使用**（照抄厂商），故意给一个不同的值
                .portList(List.of(UpdatePortItemVO.builder().containerPort(9999).port(9999).protocol("UDP").build()))
                .build();
    }

    @Test
    void rewritesPortsFromContainerPortsAndHardcodesClusterIp() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build(),
                ContainerPortVO.builder().protocol("UDP").containerPort(9090).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any())).thenReturn(java.util.Map.of("kind", "Service"));

        assertTrue(service().updateServiceDiscovery("ns", form("Service")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> specs = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(eq("ns"), eq("nps-svc"), eq("ClusterIP"), specs.capture());
        assertEquals(2, specs.getValue().size());
        var first = specs.getValue().get(0);
        assertEquals("tcp-nps-svc-0", first.getName(), "name = \"<protocol>-<serviceName>-<i>\" 小写");
        assertEquals("TCP", first.getAppProtocol());
        assertEquals("TCP", first.getProtocol());
        assertEquals(8080, first.getPort());
        assertEquals(8080, first.getTargetPort());
        assertNull(first.getNodePort(), "nodePort 显式置 null");
        assertEquals("udp-nps-svc-1", specs.getValue().get(1).getName());
        assertEquals(9090, specs.getValue().get(1).getPort());
    }

    @Test
    void emptyContainerPortsStillPatchesWithEmptyPortList() {
        // 与 /node_port 不同：A 分支**不会**因为容器没声明端口就提前返回，而是照样 patch 一份空 ports
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of());
        when(port.patchServicePorts(any(), anyString(), anyString(), any())).thenReturn(java.util.Map.of("kind", "Service"));

        assertTrue(service().updateServiceDiscovery("ns", form("Service")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> specs = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(eq("ns"), eq("nps-svc"), eq("ClusterIP"), specs.capture());
        assertTrue(specs.getValue().isEmpty());
    }

    @Test
    void missingServiceYieldsK8sCallApiFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service")));
        assertEquals("k8s.call.api.failed", e.getI18nKey(),
                "厂商的 404 走的是外层 catch(ApiException)；「service为空」那句 500 是死代码");
    }

    @Test
    void missingWorkloadYieldsServiceDiscoveryFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(null);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service")));
        assertEquals("service.update.service.discovery.failed", e.getI18nKey(),
                "厂商抛的是 SystemException(500)，但被外层 catch(Exception) 吞成 400");
    }

    @Test
    void k8sApiFailureDuringPatchYieldsK8sCallApiFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any()))
                .thenThrow(new K8sApiException("422 Unprocessable Entity"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service")));
        assertEquals("k8s.call.api.failed", e.getI18nKey());
    }

    @Test
    void otherFailureDuringPatchYieldsServiceDiscoveryFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("boom"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service")));
        assertEquals("service.update.service.discovery.failed", e.getI18nKey());
    }

    @Test
    void otherTypeIsExplicitlyNotImplemented() {
        // 厂商的另一条分支（写 g_service_port + patch 工作负载 + 通知网关）尚未实现：
        // 明确抛错，不静默成功（规格已齐，见 findings/118 第 3 节）
        SystemException e = assertThrows(SystemException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP")));
        assertTrue(e.getMessage().contains("尚未实现"));
    }
}
