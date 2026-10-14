package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
 * 配置组写类四端点（厂商 `ConfigMapController.createConfigMap:122-152` / `createConfig:276-315` /
 * `deleteConfig:246-274` / `deleteConfigMap:337-358`）。规格见 `docs/findings/144`。
 */
class ConfigMapWriteTest {

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

    // ---------- create ----------

    @Test
    void createRejectsBlankAndIllegalNames() {
        var blank = assertThrows(WrongArgumentException.class, () -> service().createConfigMap("ns", " "));
        assertEquals("configmap.name.must.not.empty", blank.getI18nKey());
        var illegal = assertThrows(WrongArgumentException.class, () -> service().createConfigMap("ns", "bad/name"));
        assertEquals("configmap.name.not.invalid", illegal.getI18nKey());
    }

    @Test
    void createRejectsExistingConfigMap() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", null)));
        var e = assertThrows(WrongArgumentException.class, () -> service().createConfigMap("ns", "cm"));
        assertEquals("configmap.existed", e.getI18nKey());
        assertEquals("cm", e.getArgs()[0]);
        verify(port, never()).createConfigMap(anyString(), anyString());
    }

    @Test
    void createCallsClusterWhenAbsent() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.empty());
        when(port.createConfigMap("ns", "cm")).thenReturn(configMap("cm", null));

        assertEquals("cm", ((Map<?, ?>) service().createConfigMap("ns", "cm").get("metadata")).get("name"));
        verify(port).createConfigMap("ns", "cm");
    }

    // ---------- upsert entry ----------

    @Test
    void upsertValidatesEntryNameAndExistence() {
        var blank = assertThrows(WrongArgumentException.class,
                () -> service().upsertConfigEntry("ns", "cm", " ", "v"));
        assertEquals("configmap.name.must.not.empty", blank.getI18nKey());

        when(port.findConfigMap("ns", "ghost")).thenReturn(Optional.empty());
        var notExist = assertThrows(WrongArgumentException.class,
                () -> service().upsertConfigEntry("ns", "ghost", "k", "v"));
        assertEquals("configmap.not.exist", notExist.getI18nKey());
        assertEquals("ghost", notExist.getArgs()[0]);
    }

    @Test
    void upsertStoresEmptyStringForBlankValueAndOverwritesSameKey() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", Map.of("old", "1"))));
        when(port.replaceConfigMapData(eq("ns"), eq("cm"), any())).thenReturn(configMap("cm", null));

        service().upsertConfigEntry("ns", "cm", "newKey", null);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(port).replaceConfigMapData(eq("ns"), eq("cm"), captor.capture());
        Map<?, ?> data = captor.getValue();
        assertEquals("", data.get("newKey"), "value 为空 ⇒ 存空串");
        assertEquals("1", data.get("old"), "既有键保留");
    }

    @Test
    void upsertReadFailureBecomesGetConfigError() {
        when(port.findConfigMap("ns", "cm"))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        var e = assertThrows(SystemException.class, () -> service().upsertConfigEntry("ns", "cm", "k", "v"));
        assertEquals("configmap.get.config.error", e.getI18nKey());
        assertEquals("cm", e.getArgs()[0]);
    }

    @Test
    void replaceFailureBecomesRequestApiError() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", null)));
        when(port.replaceConfigMapData(anyString(), anyString(), any()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        var e = assertThrows(SystemException.class, () -> service().upsertConfigEntry("ns", "cm", "k", "v"));
        assertEquals("configmap.request.api.error", e.getI18nKey());
    }

    // ---------- delete entry ----------

    @Test
    void deleteEntryRejectsMissingKey() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", Map.of("a", "1"))));
        var e = assertThrows(WrongArgumentException.class,
                () -> service().deleteConfigEntry("ns", "cm", "nope"));
        assertEquals("configmap.name.not.exist", e.getI18nKey());
        assertEquals("nope", e.getArgs()[0]);
    }

    @Test
    void deleteEntryRemovesOnlyThatKey() {
        when(port.findConfigMap("ns", "cm")).thenReturn(Optional.of(configMap("cm", Map.of("a", "1", "b", "2"))));
        when(port.replaceConfigMapData(eq("ns"), eq("cm"), any())).thenReturn(configMap("cm", null));

        service().deleteConfigEntry("ns", "cm", "a");

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(port).replaceConfigMapData(eq("ns"), eq("cm"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("2", captor.getValue().get("b"));
    }

    // ---------- delete configmaps ----------

    @Test
    void deleteConfigMapsTrimsForTheCallButReturnsTheRawValue() {
        when(port.deleteConfigMap("ns", "a")).thenReturn(true);
        when(port.deleteConfigMap("ns", "b")).thenReturn(false);

        var deleted = service().deleteConfigMaps("ns", "a, b");

        // " b" trim 后删的是 "b"，但返回值里放的是未 trim 的原值；b 删除失败 ⇒ 不在结果里
        assertEquals(List.of("a"), deleted);
        verify(port).deleteConfigMap("ns", "a");
        verify(port).deleteConfigMap("ns", "b");
    }

    @Test
    void deleteConfigMapsKeepsRawSpellingOnSuccess() {
        when(port.deleteConfigMap("ns", "b")).thenReturn(true);
        assertEquals(List.of(" b"), service().deleteConfigMaps("ns", " b"));
    }

    @Test
    void deleteConfigMapsSwallowsErrors() {
        when(port.deleteConfigMap(anyString(), anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        assertTrue(service().deleteConfigMaps("ns", "a,b").isEmpty());
    }
}
