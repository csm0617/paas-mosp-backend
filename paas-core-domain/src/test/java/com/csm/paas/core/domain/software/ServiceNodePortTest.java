package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.ContainerPortVO;
import com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO;
import com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.SystemException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/service/{serviceName}/node_port`（厂商 `patchServicePorts:701-815`）。
 * <p>**四种情形已用独立 namespace 实测**（`findings/86`）：A 分支推导、容器无端口 → `data:false`（静默）、
 * 不存在 Deployment → 500（带服务名的中文消息）、显式 nodePorts。
 */
class ServiceNodePortTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    @Test
    void branchADerivesPortsFromContainerPortsWithLowercaseNameFormula() {
        when(port.readDeploymentContainerPorts("ns", "np-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build(),
                ContainerPortVO.builder().protocol("TCP").containerPort(9090).build()));
        when(port.serviceExists("ns", "np-svc")).thenReturn(true);
        when(port.patchServicePorts(any(), any(), any(), any())).thenReturn(Map.of("kind", "Service"));

        assertTrue(service().updateServiceNodePort("ns", "np-svc", "NodePort", List.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(org.mockito.ArgumentMatchers.eq("ns"), org.mockito.ArgumentMatchers.eq("np-svc"),
                org.mockito.ArgumentMatchers.eq("NodePort"), captor.capture());
        var specs = captor.getValue();
        assertEquals(2, specs.size());
        assertEquals("tcp-np-svc-0", specs.get(0).getName(), "名字是 <protocol>-<服务名>-<下标> 的小写");
        assertEquals(8080, specs.get(0).getPort());
        assertEquals(8080, specs.get(0).getTargetPort(), "A 分支 targetPort = containerPort");
        assertEquals(null, specs.get(0).getNodePort(), "A 分支不设 nodePort（由 K8s 自动分配）");
    }

    /** 容器没有声明端口 ⇒ **静默返回 false**（不报错、也不 patch）。 */
    @Test
    void emptyContainerPortsYieldsFalseWithoutPatching() {
        when(port.readDeploymentContainerPorts("ns", "np-noport")).thenReturn(List.of());
        when(port.serviceExists("ns", "np-noport")).thenReturn(true);

        assertFalse(service().updateServiceNodePort("ns", "np-noport", "NodePort", List.of()));

        verify(port, never()).patchServicePorts(any(), any(), any(), any());
    }

    /** Deployment 不存在 ⇒ 500，且消息里**带服务名**（厂商把消息本身当参数）。 */
    @Test
    void missingDeploymentThrowsWithServiceNameInMessage() {
        when(port.readDeploymentContainerPorts("ns", "no-such")).thenReturn(null);

        var e = assertThrows(SystemException.class,
                () -> service().updateServiceNodePort("ns", "no-such", "NodePort", List.of()));
        assertTrue(e.getMessage().contains("no-such") || e.getI18nKey().contains("no-such"),
                "消息里要带服务名：" + e.getMessage());
    }

    @Test
    void branchBUsesExplicitNodePorts() {
        when(port.serviceExists("ns", "np-svc")).thenReturn(true);
        when(port.patchServicePorts(any(), any(), any(), any())).thenReturn(Map.of("kind", "Service"));

        assertTrue(service().updateServiceNodePort("ns", "np-svc", "NodePort",
                List.of(NodePortConfigVO.builder().port(8080).protocol("TCP").nodePort(30111).build())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(anyString(), anyString(), anyString(), captor.capture());
        assertEquals(30111, captor.getValue().get(0).getNodePort());
        assertEquals(8080, captor.getValue().get(0).getTargetPort(), "B 分支 targetPort = port");
    }

    @Test
    void blankTypeDefaultsToClusterIPAndBuildsNoPorts() {
        when(port.serviceExists("ns", "np-svc")).thenReturn(true);
        when(port.patchServicePorts(any(), any(), any(), any())).thenReturn(Map.of("kind", "Service"));

        assertTrue(service().updateServiceNodePort("ns", "np-svc", null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("ClusterIP"), captor.capture());
        assertTrue(captor.getValue().isEmpty(), "type 不是 NodePort 时不构建端口（照抄厂商）");
    }
}
