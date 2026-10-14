package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import com.csm.paas.core.domain.software.adapter.port.IPrometheusMetricPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.TopPodResourceVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务入口 / 重置服务 / 资源 Top 的离线契约。
 * <p>厂商出处：`SoftwareService.setServiceEntry:2238-2284`、`AppService.resetService:539-570`、
 * `SoftwareService.topPodResource:1734-1873`。
 */
class SoftwareServiceEntryAndTopTest {

    private static final String NS = "gaop-namespace";

    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IServiceRuntimePort runtimePort = mock(IServiceRuntimePort.class);
    private final IGatewayRefreshPort gatewayRefreshPort = mock(IGatewayRefreshPort.class);
    private final IPrometheusMetricPort prometheusPort = mock(IPrometheusMetricPort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        ReflectionTestUtils.setField(impl, "serviceRuntimePort", runtimePort);
        ReflectionTestUtils.setField(impl, "gatewayRefreshPort", gatewayRefreshPort);
        ReflectionTestUtils.setField(impl, "prometheusMetricPort", prometheusPort);
        ReflectionTestUtils.setField(impl, "defaultNamespace", NS);
        return impl;
    }

    // ---------- POST /v1/software/{id}/service_entry ----------

    /** 软件不存在 ⇒ **静默返回**（不报错、不写库、不通知网关）。 */
    @Test
    void serviceEntryMissingSoftwareIsSilent() {
        when(softwareRepository.queryById("sw")).thenReturn(Optional.empty());

        service().setServiceEntry(NS, "sw", "svc", 80, 1);

        verify(softwareRepository, never()).updateWebEntry(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(gatewayRefreshPort, never()).refresh();
    }

    /** 软件没绑定该服务 ⇒ 同样静默返回。 */
    @Test
    void serviceEntryUnboundServiceIsSilent() {
        when(softwareRepository.queryById("sw")).thenReturn(Optional.of(software()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw")).thenReturn(List.of("other"));

        service().setServiceEntry(NS, "sw", "svc", 80, 1);

        verify(softwareRepository, never()).updateWebEntry(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(gatewayRefreshPort, never()).refresh();
    }

    /** K8s 里没有该服务 ⇒ 静默返回。 */
    @Test
    void serviceEntryMissingK8sServiceIsSilent() {
        when(softwareRepository.queryById("sw")).thenReturn(Optional.of(software()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw")).thenReturn(List.of("svc"));
        when(runtimePort.serviceExists(NS, "svc")).thenReturn(false);

        service().setServiceEntry(NS, "sw", "svc", 80, 1);

        verify(softwareRepository, never()).updateWebEntry(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(gatewayRefreshPort, never()).refresh();
    }

    /**
     * `type=1`：① 该行 web_entry=true；② 该软件下**其它行**全部 web_entry=0；
     * ③ `router_key = "/<key>/"`；④ 通知网关。
     */
    @Test
    void serviceEntryTypeOneSetsWebEntryAndRouterKey() {
        stubHappyPath();

        service().setServiceEntry(NS, "sw", "svc", 8080, 1);

        verify(softwareRepository).updateWebEntry("sw", "svc", 8080, true);
        verify(softwareRepository).clearOtherWebEntries("sw", "svc", 8080);
        verify(softwareRepository).updateRouterKey(eq("sw"), eq("/gaop-web/"), org.mockito.ArgumentMatchers.anyLong());
        verify(gatewayRefreshPort).refresh();
    }

    /** `type=0`：该行 web_entry=false、`router_key` 置 **null**，且**不清其它行**。 */
    @Test
    void serviceEntryTypeZeroClearsWebEntryAndRouterKey() {
        stubHappyPath();

        service().setServiceEntry(NS, "sw", "svc", 8080, 0);

        verify(softwareRepository).updateWebEntry("sw", "svc", 8080, false);
        verify(softwareRepository).updateRouterKey(eq("sw"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyLong());
        verify(softwareRepository, never()).clearOtherWebEntries(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(gatewayRefreshPort).refresh();
    }

    private void stubHappyPath() {
        when(softwareRepository.queryById("sw")).thenReturn(Optional.of(software()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw")).thenReturn(List.of("svc"));
        when(runtimePort.serviceExists(NS, "svc")).thenReturn(true);
        when(runtimePort.readServiceSnapshot(NS, "svc")).thenReturn(Map.of("kind", "Service"));
    }

    private static SoftwareEntity software() {
        return SoftwareEntity.builder().id("sw").key("gaop-web").build();
    }

    // ---------- POST /v1/software/reset_service ----------

    /** 带 `ingress-lb` 标签的 Deployment **跳过**；其余全删；返回 true。 */
    @Test
    void resetServiceSkipsIngressLbDeployments() {
        Map<String, Map<String, String>> deployments = new LinkedHashMap<>();
        deployments.put("svc-a", null);
        deployments.put("ingress", Map.of("ingress-lb", "true"));
        deployments.put("svc-b", Map.of("system/appName", "gaop"));
        when(runtimePort.listDeploymentLabels(NS)).thenReturn(deployments);

        assertTrue(service().resetService(NS));

        verify(runtimePort).deleteDeployment(NS, "svc-a");
        verify(runtimePort).deleteDeployment(NS, "svc-b");
        verify(runtimePort, never()).deleteDeployment(NS, "ingress");
    }

    /** 删除失败 ⇒ 400 `service.reset.failed`。 */
    @Test
    void resetServiceFailureThrowsVendorKey() {
        when(runtimePort.listDeploymentLabels(NS)).thenReturn(Map.of("svc-a", Map.of()));
        org.mockito.Mockito.doThrow(new K8sApiException("boom"))
                .when(runtimePort).deleteDeployment(NS, "svc-a");

        var ex = assertThrows(WrongArgumentException.class, () -> service().resetService(NS));
        assertEquals("service.reset.failed", ex.getI18nKey());
    }

    // ---------- POST /v1/software/top ----------

    /** 未知 type ⇒ 榜单为空（厂商 switch 无 default）⇒ 空数组。 */
    @Test
    void topUnknownTypeYieldsEmptyList() {
        assertTrue(service().queryTopPodResources("bogus", null).isEmpty());
    }

    /** `namespace` 为空 ⇒ 用配置的命名空间（PromQL 里出现它）。 */
    @Test
    void topFallsBackToConfiguredNamespace() {
        when(prometheusPort.query(anyString())).thenReturn(List.of());

        service().queryTopPodResources("mem", "");

        verify(prometheusPort).query(org.mockito.ArgumentMatchers.contains("namespace=\"" + NS + "\""));
    }

    /**
     * 榜单出来之后**无条件**补齐其它 5 项；补齐查不到写 **`"0"`**；
     * 榜单那一项的值来自 Prometheus（字符串原样）。
     * <p>用 `thenAnswer` 区分"榜单查询"（`topk(5,`）与"补齐查询"（含 `pod_name=~`）。
     */
    @Test
    void topFillsMissingMetricsWithZeroString() {
        when(prometheusPort.query(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            if (q.startsWith("topk(5,")) {
                return List.of(TopPodResourceVO.builder().podName("pod-1").namespace(NS).value("1024").build());
            }
            return List.of();   // 补齐查询全部查不到 ⇒ "0"
        });
        when(runtimePort.readPodHostIp(NS, "pod-1")).thenReturn("10.0.0.1");
        when(runtimePort.readNodeAllocatableCpuByInternalIp("10.0.0.1")).thenReturn(java.math.BigDecimal.valueOf(4));

        var list = service().queryTopPodResources("mem", NS);

        assertEquals(1, list.size());
        var item = list.get(0);
        assertEquals("pod-1", item.getPodName());
        assertEquals(NS, item.getNamespace());
        assertEquals("1024", item.getMemory());
        assertEquals("0", item.getCpuUsageRate());
        assertEquals("0", item.getNetworkOutBound());
        assertEquals("0", item.getNetworkInBound());
        assertEquals("0", item.getDiskRead());
        assertEquals("0", item.getDiskWrite());
    }

    /** 补齐时 Prometheus 返回多条 ⇒ 厂商**只取第 0 条**。 */
    @Test
    void topUsesFirstValueWhenSeveralReturned() {
        when(prometheusPort.query(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            if (q.startsWith("topk(5,")) {
                return List.of(TopPodResourceVO.builder().podName("pod-1").namespace(NS).value("top").build());
            }
            return List.of(
                    TopPodResourceVO.builder().podName("pod-1").namespace(NS).value("first").build(),
                    TopPodResourceVO.builder().podName("pod-1").namespace(NS).value("second").build());
        });
        when(runtimePort.readPodHostIp(NS, "pod-1")).thenReturn(null);

        var list = service().queryTopPodResources("cpu", NS);

        assertEquals(1, list.size());
        assertEquals("first", list.get(0).getCpuUsageRate());
    }
}
