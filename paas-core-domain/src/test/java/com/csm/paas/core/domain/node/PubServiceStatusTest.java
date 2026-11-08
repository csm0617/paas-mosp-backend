package com.csm.paas.core.domain.node;

import com.csm.paas.core.domain.cluster.adapter.port.IPubClusterPort;
import com.csm.paas.core.domain.node.service.impl.PubServiceStatusServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/pub/serviceStatus`（厂商 `PublicController.getServiceStatusInfo:2428-2585`）。
 *
 * <p>厂商出处：`docs/findings/163-pub-cicd-and-public-extra-implemented.md`；偏离见
 * `docs/deviations.md` A-13（`sysinfo` 键名）。
 */
class PubServiceStatusTest {

    private static final String NS = "gaop-namespace";

    private IPubClusterPort port;
    private PubServiceStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        port = mock(IPubClusterPort.class);
        service = new PubServiceStatusServiceImpl();
        ReflectionTestUtils.setField(service, "namespace", NS);
        ReflectionTestUtils.setField(service, "radondbName", "gaop");
        ReflectionTestUtils.setField(service, "gisUrlInfo", "gis-server:8080");
        ReflectionTestUtils.setField(service, "pubClusterPort", port);
    }

    // ---------------------------------------------------------------- 键名

    /**
     * 厂商 `ServiceStatusInfo.sysInfo` 只标了 Gson 的 `@SerializedName`，Jackson 实际输出
     * `sysInfo`，而厂商前端读 `data.sysinfo` ⇒ 永远读不到 ⇒ 面板左列恒"正常"。
     * 本仓按前端实际读取的键名输出（A-13）。
     */
    @Test
    void sysinfoKeyIsLowercaseSoTheFrontendCanReadIt() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of());
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        Map<String, Object> body = service.serviceStatusInfo();

        assertEquals(List.of("appstatus", "sysinfo", "radondbInfo"), new ArrayList<>(body.keySet()),
                "键名与顺序：appstatus → sysinfo → radondbInfo");
        assertFalse(body.containsKey("sysInfo"), "不能再输出厂商那个前端读不到的驼峰键");
    }

    /** 厂商声明了 `loggingName = "logging"` 却从未 `infoHashMap.put`（死代码）——不复制。 */
    @Test
    void sysinfoHasTheFiveVendorKeysButNoLogging() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of());
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> sysinfo = (Map<String, Object>) service.serviceStatusInfo().get("sysinfo");

        assertEquals(List.of("k8s", "dns", "monitor", "apiserver", "etcd"), new ArrayList<>(sysinfo.keySet()));
        assertFalse(sysinfo.containsKey("logging"), "厂商那行 logging 是死代码");
    }

    /** 命不中 pod 时 `status` 保持 null（不是 "error"）——厂商 `getSystemInfo:2616-2637`。 */
    @Test
    void missingPodLeavesStatusNull() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of());
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> sysinfo = (Map<String, Object>) service.serviceStatusInfo().get("sysinfo");

        assertEquals("Running", info(sysinfo, "k8s").get("status"), "k8s 是硬编码常量");
        assertNull(info(sysinfo, "dns").get("status"));
        assertNull(info(sysinfo, "monitor").get("status"));
        assertEquals("v0.34.0", info(sysinfo, "monitor").get("version"), "version 也是硬编码");
    }

    /** 命中 pod 且容器 ready ⇒ Running；未 ready ⇒ error。 */
    @Test
    void podHitDrivesStatusByContainerReady() {
        when(port.listAllPodSnapshots()).thenReturn(List.of(
                pod("kube-dns-abc", "kube-system", Map.of("k8s-app", "kube-dns"), true)));
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of());
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> sysinfo = (Map<String, Object>) service.serviceStatusInfo().get("sysinfo");

        assertEquals("Running", info(sysinfo, "dns").get("status"));
    }

    // ---------------------------------------------------------------- appstatus

    /** 只收 `labels["system/appName"] == "gaop"` 的 Deployment（厂商 `:2480-2489`）。 */
    @Test
    void onlyDeploymentsWithAppNameGaopAreListed() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of(
                deployment("gaop-be", Map.of("system/appName", "gaop")),
                deployment("paas-web", Map.of("app", "paas-web"))));
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> appstatus = (Map<String, Object>) service.serviceStatusInfo().get("appstatus");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deployments = (List<Map<String, Object>>) appstatus.get("deployments");

        assertEquals(1, deployments.size());
        assertEquals("gaop-be", name(deployments.get(0)));
    }

    /** pods 按 `labels["name"] == deployment 名` 匹配（厂商 `:2532-2538`）。 */
    @Test
    void podsAreMatchedByTheNameLabel() {
        when(port.listAllPodSnapshots()).thenReturn(List.of(
                pod("gaop-be-6c55674c9c-9524c", NS, Map.of("app", "gaop-be", "name", "gaop-be"), true),
                pod("gaop-be-stale-pod", NS, Map.of("app", "gaop-be"), true)));
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of(
                deployment("gaop-be", Map.of("system/appName", "gaop"))));
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> appstatus = (Map<String, Object>) service.serviceStatusInfo().get("appstatus");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pods = (List<Map<String, Object>>) appstatus.get("pods");

        assertEquals(1, pods.size(), "没有 name 标签的 pod 不算");
        assertEquals("gaop-be-6c55674c9c-9524c", name(pods.get(0)));
    }

    // ---------------------------------------------------------------- radondb

    /** 没有 `<radondb.name>-radondb-mysql` 的 StatefulSet ⇒ `radondbInfo` 为 null。 */
    @Test
    void radondbInfoStaysNullWithoutRadondbPods() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS)).thenReturn(List.of());
        when(port.readDeploymentSnapshot(anyString(), anyString())).thenReturn(null);

        assertNull(service.serviceStatusInfo().get("radondbInfo"));
    }

    /** 列 Deployment 抛 K8sApiException ⇒ 厂商提前返回半成品（appstatus / radondbInfo 都是 null）。 */
    @Test
    void deploymentApiFailureReturnsThePartialBody() {
        when(port.listAllPodSnapshots()).thenReturn(List.of());
        when(port.listDeploymentSnapshots(NS))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));

        Map<String, Object> body = service.serviceStatusInfo();

        assertNull(body.get("appstatus"));
        assertNull(body.get("radondbInfo"));
        assertTrue(body.containsKey("sysinfo"), "半成品里 sysinfo 仍在");
    }

    // ---------------------------------------------------------------- fixture

    private static Map<String, Object> info(Map<String, Object> sysinfo, String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) sysinfo.get(key);
        return info;
    }

    private static String name(Map<String, Object> snapshot) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) snapshot.get("metadata");
        return String.valueOf(metadata.get("name"));
    }

    private static Map<String, Object> deployment(String name, Map<String, String> labels) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", NS);
        metadata.put("labels", labels);
        metadata.put("generation", 1L);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("replicas", 1);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("availableReplicas", 1);
        status.put("readyReplicas", 1);
        status.put("replicas", 1);
        status.put("updatedReplicas", 1);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metadata", metadata);
        out.put("spec", spec);
        out.put("status", status);
        return out;
    }

    private static Map<String, Object> pod(String name, String namespace, Map<String, String> labels, boolean ready) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        metadata.put("labels", labels);
        metadata.put("generateName", null);

        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> running = new LinkedHashMap<>();
        running.put("startedAt", "2026-11-08T00:00:00Z");
        state.put("running", running);

        Map<String, Object> containerStatus = new LinkedHashMap<>();
        containerStatus.put("ready", ready);
        containerStatus.put("restartCount", 0);
        containerStatus.put("state", state);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("phase", "Running");
        status.put("containerStatuses", List.of(containerStatus));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metadata", metadata);
        out.put("status", status);
        return out;
    }
}
