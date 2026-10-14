package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/config_map/{name}/exist`、`GET /v1/config_map/{name}`、`GET /v1/config_map/relationService`
 * （厂商 `ConfigMapController.isExistConfigmap:98-110` / `configmap:155-182` / `configMapRelationService:363-432`）。
 * <p>规格见 `docs/findings/143`。
 */
class ConfigMapReadTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> configMap(String name, Map<String, String> data) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        Map<String, Object> m = new HashMap<>();
        m.put("metadata", metadata);
        if (data != null) {
            m.put("data", new HashMap<>(data));
        }
        return m;
    }

    /** 造一个 Deployment：一个 configMap 卷 + 一个带 subPath 的挂载。 */
    private static Map<String, Object> deployment(String name, String cmName, String volumeName,
                                                 String subPath, String mountPath) {
        Map<String, Object> mount = new HashMap<>();
        mount.put("name", volumeName);
        mount.put("subPath", subPath);
        mount.put("mountPath", mountPath);
        Map<String, Object> container = new HashMap<>();
        container.put("name", "app");
        container.put("volumeMounts", List.of(mount));
        Map<String, Object> volume = new HashMap<>();
        volume.put("name", volumeName);
        volume.put("configMap", Map.of("name", cmName));
        Map<String, Object> podSpec = new HashMap<>();
        podSpec.put("volumes", List.of(volume));
        podSpec.put("containers", List.of(container));
        Map<String, Object> template = new HashMap<>();
        template.put("spec", podSpec);
        Map<String, Object> spec = new HashMap<>();
        spec.put("template", template);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        Map<String, Object> d = new HashMap<>();
        d.put("metadata", metadata);
        d.put("spec", spec);
        return d;
    }

    // ---------- exist ----------

    @Test
    void existTrueWhenFound() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", null)));
        assertTrue(service().configMapExists("ns", "cm"));
    }

    @Test
    void existFalseWhenNotFound() {
        when(port.findConfigMap("ns", "ghost")).thenReturn(Optional.empty());
        assertFalse(service().configMapExists("ns", "ghost"));
    }

    @Test
    void existOtherErrorBecomesSystemException() {
        when(port.findConfigMap(anyString(), anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        var e = assertThrows(SystemException.class, () -> service().configMapExists("ns", "cm"));
        assertEquals("configmap.system.error", e.getI18nKey());
    }

    // ---------- detail ----------

    @Test
    void blankNameIsRejected() {
        var e = assertThrows(WrongArgumentException.class, () -> service().queryConfigMap("ns", " "));
        assertEquals("configmap.name.must.not.empty", e.getI18nKey());
    }

    @Test
    void illegalNameIsRejected() {
        var e = assertThrows(WrongArgumentException.class, () -> service().queryConfigMap("ns", "bad/name"));
        assertEquals("configmap.name.not.invalid", e.getI18nKey());
    }

    @Test
    void notFoundBecomesWrongArgumentWithName() {
        when(port.findConfigMap("ns", "ghost")).thenReturn(Optional.empty());
        var e = assertThrows(WrongArgumentException.class, () -> service().queryConfigMap("ns", "ghost"));
        assertEquals("configmap.not.exist", e.getI18nKey());
        assertEquals("ghost", e.getArgs()[0]);
    }

    // ---------- relationService ----------

    @Test
    void relationsAreBuiltFromDataKeysAndVolumeSubPaths() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(
                configMap("cm", Map.of("app.conf", "a=1", "log.conf", "b=2"))));
        when(port.listDeploymentSnapshots("ns")).thenReturn(List.of(
                deployment("svc-a", "cm", "vol", "app.conf", "/etc/app/app.conf"),
                deployment("svc-b", "other-cm", "vol", "app.conf", "/etc/x"),
                deployment("svc-c", "cm", "vol", "not-a-key", "/etc/y")));

        var result = service().queryConfigMapRelations("ns", "cm");

        assertEquals(2, result.size());
        // 按 configMapName 排序：app.conf 在前
        assertEquals("app.conf", result.get(0).getConfigMapName());
        assertEquals(1, result.get(0).getConfig().size());
        assertEquals("svc-a", result.get(0).getConfig().get(0).getServiceName());
        assertEquals("/etc/app/app.conf", result.get(0).getConfig().get(0).getMountPath());
        assertEquals("log.conf", result.get(1).getConfigMapName());
        assertTrue(result.get(1).getConfig().isEmpty(), "没人挂载的 key 也要出现（空列表）");
    }

    @Test
    void multipleContainersAndMountsAreAllCollected() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(
                configMap("cm", Map.of("k", "v"))));
        Map<String, Object> d = deployment("svc", "cm", "vol", "k", "/m1");
        when(port.listDeploymentSnapshots("ns")).thenReturn(List.of(d, deployment("svc2", "cm", "vol", "k", "/m2")));

        var result = service().queryConfigMapRelations("ns", "cm");

        assertEquals(2, result.get(0).getConfig().size());
    }

    @Test
    void missingConfigMapYieldsEmptyList() {
        when(port.findConfigMap("ns", "ghost")).thenReturn(Optional.empty());
        assertTrue(service().queryConfigMapRelations("ns", "ghost").isEmpty());
    }

    @Test
    void clusterErrorYieldsEmptyList() {
        when(port.findConfigMap(anyString(), anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        assertTrue(service().queryConfigMapRelations("ns", "cm").isEmpty());
    }

    @Test
    void configMapWithoutDataYieldsEmptyList() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", null)));
        assertTrue(service().queryConfigMapRelations("ns", "cm").isEmpty());
    }
}
