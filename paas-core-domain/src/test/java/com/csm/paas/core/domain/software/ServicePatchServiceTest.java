package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ContainerPortVO;
import com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO;
import com.csm.paas.core.domain.software.model.valobj.ServicePortVO;
import com.csm.paas.core.domain.software.model.valobj.UpdatePortItemVO;
import com.csm.paas.core.domain.software.model.valobj.UpdateServiceDiscoveryVO;
import com.csm.paas.core.domain.software.model.valobj.WorkloadRefVO;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/patchService`（厂商 `ServiceController.updateServiceDiscovery:1014-1163`）。
 *
 * <p>A 分支（`type == "Service"`）：ports 由**容器端口**推导、`type` 写死 `ClusterIP`、错误映射两条 400。
 * <p>B 分支（`type != "Service"`）：写 `g_service_port` + patch 工作负载容器端口 + 通知网关，
 * 工作负载读不到是 **500**（与 A 分支的 400 形成对照）。
 * <p>规格见 `docs/findings/118`。
 */
class ServicePatchServiceTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IGatewayRefreshPort gatewayRefreshPort = mock(IGatewayRefreshPort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "gatewayRefreshPort", gatewayRefreshPort);
        return impl;
    }

    private static UpdateServiceDiscoveryVO form(String type, List<UpdatePortItemVO> portList) {
        return UpdateServiceDiscoveryVO.builder()
                .type(type).serviceName("nps-svc").portList(portList).build();
    }

    private static UpdatePortItemVO item(int containerPort, int port, String protocol) {
        return UpdatePortItemVO.builder()
                .containerPort(containerPort).port(port).protocol(protocol).build();
    }

    // ==================== A 分支 ====================

    @Test
    void rewritesPortsFromContainerPortsAndHardcodesClusterIp() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build(),
                ContainerPortVO.builder().protocol("UDP").containerPort(9090).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any())).thenReturn(java.util.Map.of("kind", "Service"));

        // 入参的 portList 在 A 分支**完全不被使用**（照抄厂商），故意给一个不同的值
        assertTrue(service().updateServiceDiscovery("ns",
                form("Service", List.of(item(9999, 9999, "UDP")))));

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

        assertTrue(service().updateServiceDiscovery("ns", form("Service", null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> specs = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(eq("ns"), eq("nps-svc"), eq("ClusterIP"), specs.capture());
        assertTrue(specs.getValue().isEmpty());
    }

    @Test
    void missingServiceYieldsK8sCallApiFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service", null)));
        assertEquals("k8s.call.api.failed", e.getI18nKey(),
                "厂商的 404 走的是外层 catch(ApiException)；「service为空」那句 500 是死代码");
    }

    @Test
    void missingWorkloadYieldsServiceDiscoveryFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(null);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service", null)));
        assertEquals("service.update.service.discovery.failed", e.getI18nKey(),
                "厂商抛的是 SystemException(500)，但被外层 catch(Exception) 吞成 400");
    }

    @Test
    void k8sApiFailureDuringServicePatchYieldsK8sCallApiFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any()))
                .thenThrow(new K8sApiException("422 Unprocessable Entity"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service", null)));
        assertEquals("k8s.call.api.failed", e.getI18nKey());
    }

    @Test
    void otherFailureDuringServicePatchYieldsServiceDiscoveryFailed() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build()));
        when(port.patchServicePorts(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("boom"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("Service", null)));
        assertEquals("service.update.service.discovery.failed", e.getI18nKey());
    }

    // ==================== B 分支 ====================

    private void workload(String name, boolean deployment) {
        when(port.readWorkloadRef("ns", "nps-svc"))
                .thenReturn(WorkloadRefVO.builder().deployment(deployment).name(name).build());
    }

    @Test
    void writesServicePortsAndPatchesAllContainers() {
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of("s1"));

        assertTrue(service().updateServiceDiscovery("ns", form("ClusterIP", List.of(
                item(8080, 80, "TCP"), item(9090, 90, "UDP")))));

        verify(softwareRepository).deleteServicePorts("s1", "nps-svc");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortVO>> rows = ArgumentCaptor.forClass(List.class);
        verify(softwareRepository).insertServicePorts(rows.capture());
        assertEquals(2, rows.getValue().size());
        var first = rows.getValue().get(0);
        assertEquals("s1", first.getSoftwareId());
        assertEquals("nps-svc", first.getServiceName());
        assertEquals(80, first.getPort(), "写库用的是 `port`（映射端口），不是 containerPort");
        assertEquals("nps-svc-80", first.getPathName());
        assertEquals(Boolean.TRUE, first.getAllow());
        assertEquals(Boolean.FALSE, first.getWebEntry());
        assertTrue(first.getUpdatedAt() > 0);
        assertEquals("nps-svc-90", rows.getValue().get(1).getPathName());
        verify(gatewayRefreshPort).refresh();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContainerPortVO>> cp = ArgumentCaptor.forClass(List.class);
        verify(port).patchWorkloadContainerPorts(eq("ns"), eq("nps-svc"), eq(true), cp.capture());
        assertEquals(2, cp.getValue().size());
        assertEquals(8080, cp.getValue().get(0).getContainerPort(), "容器端口用的是 containerPort");
        assertEquals("TCP", cp.getValue().get(0).getProtocol());
        assertEquals(9090, cp.getValue().get(1).getContainerPort());
    }

    @Test
    void emptySoftwareAppsStillPatchesWorkloadWithoutDbOrGateway() {
        workload("nps-svc", false);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of());

        assertTrue(service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));

        verify(softwareRepository, never()).deleteServicePorts(anyString(), anyString());
        verify(softwareRepository, never()).insertServicePorts(any());
        verifyNoInteractions(gatewayRefreshPort);
        verify(port).patchWorkloadContainerPorts(eq("ns"), eq("nps-svc"), eq(false), any());
    }

    @Test
    void emptyPortListIsAllowedWhenNoSoftwareAppMatches() {
        // 与下一条对照：software_app 为空时，空 portList 会**清空**容器端口并返回 200
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of());

        assertTrue(service().updateServiceDiscovery("ns", form("ClusterIP", List.of())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContainerPortVO>> cp = ArgumentCaptor.forClass(List.class);
        verify(port).patchWorkloadContainerPorts(eq("ns"), eq("nps-svc"), eq(true), cp.capture());
        assertTrue(cp.getValue().isEmpty(), "容器端口被清空");
    }

    @Test
    void emptyPortListWithSoftwareAppsIsRejected() {
        // 厂商 `insertBatch` 是 `<foreach>`：空列表拼出非法 SQL ⇒ 400
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of("s1"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP", List.of())));
        assertEquals("service.update.service.details.failed", e.getI18nKey());
        verify(port, never()).patchWorkloadContainerPorts(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void nullPortListIsRejected() {
        workload("nps-svc", true);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP", null)));
        assertEquals("service.update.service.details.failed", e.getI18nKey(),
                "厂商 `:1108` 直接 .forEach ⇒ NPE ⇒ 被 catch(Exception) 吞成 400");
    }

    @Test
    void missingWorkloadInBranchBIsA500NotA400() {
        when(port.readWorkloadRef("ns", "nps-svc")).thenReturn(null);

        SystemException e = assertThrows(SystemException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));
        assertTrue(e.getMessage().contains("修改dployment/statefulSet失败，找不到该服务"),
                "厂商这条 SystemException 抛在 try 外面 ⇒ 真的 500");
    }

    @Test
    void k8sFailureDuringWorkloadPatchYieldsConfigError() {
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new K8sApiException("boom"))
                .when(port).patchWorkloadContainerPorts(anyString(), anyString(), anyBoolean(), any());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));
        assertEquals("service.update.service.config.error", e.getI18nKey());
    }

    @Test
    void dbFailureYieldsDetailsFailed() {
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of("s1"));
        org.mockito.Mockito.doThrow(new IllegalStateException("sql error"))
                .when(softwareRepository).insertServicePorts(any());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));
        assertEquals("service.update.service.details.failed", e.getI18nKey());
    }

    @Test
    void dbLookupAndPathNameUseWorkloadMetadataNameNotRequestName() {
        // 厂商用的是 `metadata.name`（`deploymentName`），不是请求里的 serviceName
        workload("real-workload", true);
        when(softwareRepository.querySoftwareIdsByAppName("real-workload")).thenReturn(List.of("s1"));

        assertTrue(service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));

        verify(softwareRepository).querySoftwareIdsByAppName("real-workload");
        verify(softwareRepository).deleteServicePorts("s1", "real-workload");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortVO>> rows = ArgumentCaptor.forClass(List.class);
        verify(softwareRepository).insertServicePorts(rows.capture());
        assertEquals("real-workload-80", rows.getValue().get(0).getPathName());
        // 但 k8s 侧的读/写仍然用请求里的 serviceName
        verify(port).readWorkloadRef("ns", "nps-svc");
        verify(port).patchWorkloadContainerPorts(eq("ns"), eq("nps-svc"), eq(true), any());
    }

    @Test
    void multipleSoftwareIdsEachGetTheirOwnRows() {
        workload("nps-svc", true);
        when(softwareRepository.querySoftwareIdsByAppName("nps-svc")).thenReturn(List.of("s1", "s2"));

        assertTrue(service().updateServiceDiscovery("ns", form("ClusterIP", List.of(item(8080, 80, "TCP")))));

        verify(softwareRepository).deleteServicePorts("s1", "nps-svc");
        verify(softwareRepository).deleteServicePorts("s2", "nps-svc");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortVO>> rows = ArgumentCaptor.forClass(List.class);
        verify(softwareRepository, org.mockito.Mockito.times(2)).insertServicePorts(rows.capture());
        assertEquals("s1", rows.getAllValues().get(0).get(0).getSoftwareId());
        assertEquals("s2", rows.getAllValues().get(1).get(0).getSoftwareId());
        verify(gatewayRefreshPort, org.mockito.Mockito.times(1)).refresh();
    }
}
