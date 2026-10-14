package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/config_map/import`（厂商 `SoftwareService.importConfigMap:3363-3403`）。
 * <p>规格见 `docs/findings/146`。重点：存在 ⇒ replace、不存在 ⇒ create；
 * **失败只记日志**（恒返回 true）；替换/创建前把 `resourceVersion`/`uid` **置 null**。
 */
class ConfigMapImportTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static Map<String, Object> cm(String name, String resourceVersion, String uid) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        if (resourceVersion != null) {
            metadata.put("resourceVersion", resourceVersion);
        }
        if (uid != null) {
            metadata.put("uid", uid);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("metadata", metadata);
        m.put("data", Map.of("k", "v"));
        return m;
    }

    @Test
    void emptyOrNullListIsTrueWithoutClusterCalls() {
        assertTrue(service().importConfigMaps("ns", List.of()));
        assertTrue(service().importConfigMaps("ns", null));
        verify(port, never()).createConfigMapObject(anyString(), any());
        verify(port, never()).replaceConfigMapObject(anyString(), any());
    }

    @Test
    void existingConfigMapIsReplaced() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(cm("cm", null, null)));

        assertTrue(service().importConfigMaps("ns", List.of(cm("cm", "123", "uid-1"))));

        verify(port).replaceConfigMapObject(eq("ns"), any());
        verify(port, never()).createConfigMapObject(anyString(), any());
    }

    @Test
    void missingConfigMapIsCreated() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.empty());

        assertTrue(service().importConfigMaps("ns", List.of(cm("cm", null, null))));

        verify(port).createConfigMapObject(eq("ns"), any());
        verify(port, never()).replaceConfigMapObject(anyString(), any());
    }

    @Test
    void resourceVersionAndUidAreClearedBeforeWriting() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.empty());

        service().importConfigMaps("ns", List.of(cm("cm", "123", "uid-1")));

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(port).createConfigMapObject(eq("ns"), captor.capture());
        Map<?, ?> metadata = (Map<?, ?>) captor.getValue().get("metadata");
        assertFalse(metadata.containsKey("resourceVersion"));
        assertFalse(metadata.containsKey("uid"));
        assertEquals("cm", metadata.get("name"));
    }

    @Test
    void readFailureFallsBackToCreateAndKeepsGoing() {
        when(port.findConfigMap("ns", "a")).thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        when(port.findConfigMap("ns", "b")).thenReturn(Optional.of(cm("b", null, null)));

        assertTrue(service().importConfigMaps("ns", List.of(cm("a", null, null), cm("b", null, null))));

        verify(port).createConfigMapObject(eq("ns"), any());
        verify(port).replaceConfigMapObject(eq("ns"), any());
    }

    @Test
    void perItemFailureIsSwallowedAndAlwaysTrue() {
        when(port.findConfigMap(anyString(), anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"))
                .when(port).createConfigMapObject(anyString(), any());

        assertTrue(service().importConfigMaps("ns", List.of(cm("a", null, null), cm("b", null, null))));
        verify(port, org.mockito.Mockito.times(2)).createConfigMapObject(eq("ns"), any());
    }

    @Test
    void missingMetadataDoesNotBlowUp() {
        Map<String, Object> noMetadata = new HashMap<>();
        noMetadata.put("data", Map.of("k", "v"));
        when(port.findConfigMap("ns", null)).thenReturn(Optional.empty());

        assertTrue(service().importConfigMaps("ns", List.of(noMetadata)));
        verify(port).createConfigMapObject(eq("ns"), any());
    }
}
