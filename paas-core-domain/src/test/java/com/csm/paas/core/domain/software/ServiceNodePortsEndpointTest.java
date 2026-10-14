package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.ContainerPortVO;
import com.csm.paas.core.domain.software.model.valobj.NodePortConfigVO;
import com.csm.paas.core.domain.software.model.valobj.ServicePortSpecVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
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
 * `POST /v1/service/{serviceName}/nodePort`（厂商 `updateServicePort:482-670`）。
 * <p><b>实测结论（`findings/89`）</b>：这个端点**在任何可达输入下都无法成功**——
 * type=ClusterIP（唯一会走到创建分支的 type）时：
 * 带 nodePort ⇒ K8s 422「nodePort forbidden when type is ClusterIP」；
 * 不带 nodePort ⇒ K8s 422「spec.ports: Required value」；
 * type != ClusterIP ⇒ 直接返回 false。我们照抄其可观测行为。
 */
class ServiceNodePortsEndpointTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private void happyPath() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of(
                ContainerPortVO.builder().protocol("TCP").containerPort(8080).build()));
        when(port.serviceExists("ns", "nps-svc-nodeport")).thenReturn(false);
        when(port.listUsedNodePorts("ns")).thenReturn(List.of());
        when(port.createNodePortService(any(), any(), any(), any(), any())).thenReturn(Map.of("kind", "Service"));
    }

    @Test
    void patchesMainServiceWithHardcodedClusterIpThenCreatesNodePortService() {
        happyPath();

        assertTrue(service().updateServiceNodePortsEndpoint("ns", "nps-svc", "ClusterIP",
                List.of(NodePortConfigVO.builder().port(8080).protocol("TCP").build())));

        // 主 Service：type 写死 ClusterIP、ports 由容器端口推导、nodePort 显式 null
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> main = ArgumentCaptor.forClass(List.class);
        verify(port).patchServicePorts(org.mockito.ArgumentMatchers.eq("ns"),
                org.mockito.ArgumentMatchers.eq("nps-svc"),
                org.mockito.ArgumentMatchers.eq("ClusterIP"), main.capture());
        assertEquals("tcp-nps-svc-0", main.getValue().get(0).getName());
        assertEquals(null, main.getValue().get(0).getNodePort());
        // <name>-nodeport：用请求的 type
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> np = ArgumentCaptor.forClass(List.class);
        verify(port).createNodePortService(org.mockito.ArgumentMatchers.eq("ns"),
                org.mockito.ArgumentMatchers.eq("nps-svc-nodeport"),
                org.mockito.ArgumentMatchers.eq("ClusterIP"), np.capture(),
                org.mockito.ArgumentMatchers.eq("nps-svc"));
    }

    /** `nodePort` 为空 → **随机**分配，必须落在厂商的区间 [10000, 75534]。 */
    @Test
    void emptyNodePortIsRandomlyAllocatedInVendorRange() {
        happyPath();

        service().updateServiceNodePortsEndpoint("ns", "nps-svc", "ClusterIP",
                List.of(NodePortConfigVO.builder().port(8080).protocol("TCP").nodePort(null).build()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServicePortSpecVO>> np = ArgumentCaptor.forClass(List.class);
        verify(port).createNodePortService(anyString(), anyString(), anyString(), np.capture(), anyString());
        Integer generated = np.getValue().get(0).getNodePort();
        assertTrue(generated >= 10000 && generated <= 10000 + 65535, "厂商区间 [10000, 75534]：" + generated);
    }

    /** type 不是 ClusterIP ⇒ 返回 false，且**不**碰 `<name>-nodeport`。 */
    @Test
    void nonClusterIpTypeReturnsFalseWithoutCreating() {
        when(port.serviceExists("ns", "nps-svc")).thenReturn(true);
        when(port.readWorkloadContainerPorts("ns", "nps-svc")).thenReturn(List.of());

        assertFalse(service().updateServiceNodePortsEndpoint("ns", "nps-svc", "LoadBalancer", List.of()));

        verify(port, never()).createNodePortService(any(), any(), any(), any(), any());
    }

    /** K8s API 异常 → 400 `service.port.occupied.error`（厂商把 422 映射到这个键，**消息会误导**，照抄）。 */
    @Test
    void k8sApiExceptionMapsToPortOccupiedKey() {
        happyPath();
        when(port.createNodePortService(any(), any(), any(), any(), any()))
                .thenThrow(new K8sApiException("spec.ports[0].nodePort: Forbidden"));

        var e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceNodePortsEndpoint("ns", "nps-svc", "ClusterIP",
                        List.of(NodePortConfigVO.builder().port(8080).protocol("TCP").nodePort(30222).build())));
        assertEquals("service.port.occupied.error", e.getI18nKey());
    }

    /** 其它异常 → 400 `service.create.node.port.failed`。 */
    @Test
    void otherExceptionMapsToCreateFailedKey() {
        happyPath();
        when(port.createNodePortService(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        var e = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceNodePortsEndpoint("ns", "nps-svc", "ClusterIP", List.of()));
        assertEquals("service.create.node.port.failed", e.getI18nKey());
    }
}
