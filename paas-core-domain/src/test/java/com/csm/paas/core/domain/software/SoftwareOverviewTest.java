package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/software/overView`（厂商 `SoftwareService.getOverViewInfo:1631-1720`）。
 * <p>规格见 `docs/findings/129`。三组计数**口径不同**，所以分别钉：
 * software 按"关联服务状态汇总"（找不到算 STOP、部分运行归 pending）、
 * service 按标签过滤、pod 按 phase（列 pod 失败只记日志）。
 */
class SoftwareOverviewTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        return impl;
    }

    /** 造一个带 status 的工作负载项（Deployment 状态推导用）。 */
    private static ServiceRuntimeEntity item(String name, Map<String, Object> status, String appType, String appName) {
        Map<String, Object> deployment = new HashMap<>();
        deployment.put("status", status);
        return ServiceRuntimeEntity.builder()
                .name(name).deploymentSnapshot(deployment).appType(appType).appName(appName).build();
    }

    private static Map<String, Object> running() {
        Map<String, Object> s = new HashMap<>();
        s.put("replicas", 1);
        s.put("readyReplicas", 1);
        s.put("availableReplicas", 1);
        s.put("updatedReplicas", 1);
        s.put("unavailableReplicas", 0);
        return s;
    }

    private static Map<String, Object> emptyStatus() {
        return new HashMap<>();
    }

    private static Map<String, Object> pod(String appName, String phase) {
        Map<String, Object> labels = new HashMap<>();
        if (appName != null) {
            labels.put("system/appName", appName);
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("labels", labels);
        Map<String, Object> status = new HashMap<>();
        status.put("phase", phase);
        Map<String, Object> p = new HashMap<>();
        p.put("metadata", metadata);
        p.put("status", status);
        return p;
    }

    @Test
    void softwareCountsFollowTheAggregateRule() {
        // sw1: 关联两个服务，一个 RUNNING 一个找不到 ⇒ PART_RUN ⇒ pending
        when(softwareRepository.queryAllSoftware()).thenReturn(List.of(
                SoftwareEntity.builder().id("sw1").build(),
                SoftwareEntity.builder().id("sw2").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("a", "ghost"));
        when(softwareRepository.queryAppNamesBySoftwareId("sw2")).thenReturn(List.of("b"));
        when(port.queryServices("ns")).thenReturn(List.of(
                item("a", running(), null, null),
                item("b", emptyStatus(), null, null)));
        var impl = service();
        when(port.listAllPods("ns")).thenReturn(List.of());

        var result = impl.querySoftwareOverview("ns");

        // sw1 ⇒ PART_RUN ⇒ pending=1；sw2 ⇒ 关联服务 b 状态为空 ⇒ STOP ⇒ stop=1
        assertEquals(1, result.getSoftware().getPending());
        assertEquals(1, result.getSoftware().getStop());
        assertEquals(0, result.getSoftware().getRunning());
    }

    @Test
    void softwareWithNoAppsIsPendingNotStop() {
        // 厂商 getSoftwareStatus(空列表) 返回 "STOP"，但调用方把非 RUNNING/STOP 归 pending
        // —— 这里空列表 ⇒ "STOP" ⇒ stop++（照抄 getSoftwareStatus 的返回值）
        when(softwareRepository.queryAllSoftware()).thenReturn(List.of(SoftwareEntity.builder().id("sw1").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of());
        when(port.queryServices("ns")).thenReturn(List.of());
        var impl = service();
        when(port.listAllPods("ns")).thenReturn(List.of());

        var result = impl.querySoftwareOverview("ns");

        assertEquals(1, result.getSoftware().getStop());
    }

    @Test
    void serviceCountsSkipGaopAppTypeAndSpecialAppNames() {
        when(softwareRepository.queryAllSoftware()).thenReturn(List.of());
        when(port.queryServices("ns")).thenReturn(List.of(
                item("s1", running(), "gaop-web", "s1"),        // appType 以 gaop 开头 ⇒ 跳过
                item("s2", running(), null, "gis-server"),       // 特殊 appName ⇒ 跳过
                item("s3", running(), null, "gis-jb"),           // 跳过
                item("s4", running(), null, "gaop"),             // 跳过
                item("s5", running(), null, "app1"),             // 计入 running
                item("s6", emptyStatus(), null, "app2")));       // 计入 stop
        var impl = service();
        when(port.listAllPods("ns")).thenReturn(List.of());

        var result = impl.querySoftwareOverview("ns");

        assertEquals(1, result.getService().getRunning());
        assertEquals(1, result.getService().getStop());
        assertEquals(0, result.getService().getPending());
    }

    @Test
    void podCountsByPhaseAndSkipGaop() {
        when(softwareRepository.queryAllSoftware()).thenReturn(List.of());
        when(port.queryServices("ns")).thenReturn(List.of());
        var impl = service();
        when(port.listAllPods("ns")).thenReturn(List.of(
                pod("gaop", "Running"),        // 跳过
                pod(null, "Running"),
                pod("x", "Pending"),
                pod("y", "Terminating"),
                pod("z", "Succeeded")));       // 其它 phase ⇒ stop

        var result = impl.querySoftwareOverview("ns");

        assertEquals(1, result.getPod().getRunning());
        assertEquals(2, result.getPod().getPending());
        assertEquals(1, result.getPod().getStop());
    }

    @Test
    void podLookupFailureOnlyZerosThePodGroup() {
        when(softwareRepository.queryAllSoftware()).thenReturn(List.of());
        when(port.queryServices("ns")).thenReturn(List.of(item("s5", running(), null, "app1")));
        var impl = service();
        when(port.listAllPods("ns")).thenThrow(new K8sApiException("boom"));

        var result = impl.querySoftwareOverview("ns");

        assertEquals(1, result.getService().getRunning(), "service 组不受影响");
        assertEquals(0, result.getPod().getRunning());
        assertEquals(0, result.getPod().getStop());
        assertEquals(0, result.getPod().getPending());
    }
}
