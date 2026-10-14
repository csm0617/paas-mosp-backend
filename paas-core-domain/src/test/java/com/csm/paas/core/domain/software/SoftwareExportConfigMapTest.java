package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
 * `POST /v1/software/exportConfigmap`（厂商 `exportConfigMapBySoftwareOrService:3915-4018` +
 * `exportConfigMap:3453-3477`）。规格见 `docs/findings/134`。
 * <p>与 `existConfigmap` 的两处不同是重点：**service 分支读不到 Deployment ⇒ 400**；
 * 收集完后要**按名读 ConfigMap**（读不到的跳过）。
 */
class SoftwareExportConfigMapTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        return impl;
    }

    private static Map<String, Object> cm(String name) {
        return Map.of("metadata", Map.of("name", name));
    }

    @Test
    void softwareTypeCollectsViaRelatedServices() {
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw1").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("svc-a", "svc-b"));
        when(port.readDeploymentConfigMapNames("ns", "svc-a")).thenReturn(List.of("cm-1"));
        when(port.readDeploymentConfigMapNames("ns", "svc-b")).thenReturn(List.of("cm-2", "cm-1"));
        when(port.readConfigMaps("ns", List.of("cm-1", "cm-2"))).thenReturn(List.of(cm("cm-1"), cm("cm-2")));

        var result = service().collectConfigMapsForExport("ns", "software", List.of("sw1"));

        assertEquals(2, result.size(), "名字要去重（LinkedHashSet）");
        verify(port).readConfigMaps("ns", List.of("cm-1", "cm-2"));
    }

    @Test
    void serviceTypeUsesTheOrThrowVariant() {
        when(port.readDeploymentConfigMapNamesOrThrow("ns", "dep-1")).thenReturn(List.of("cm-1"));
        when(port.readConfigMaps("ns", List.of("cm-1"))).thenReturn(List.of(cm("cm-1")));

        var result = service().collectConfigMapsForExport("ns", "service", List.of("dep-1"));

        assertEquals(1, result.size());
        verify(port).readDeploymentConfigMapNamesOrThrow("ns", "dep-1");
    }

    @Test
    void missingDeploymentInServiceBranchYields400WithKey() {
        when(port.readDeploymentConfigMapNamesOrThrow("ns", "ghost"))
                .thenThrow(new K8sApiException("not found"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().collectConfigMapsForExport("ns", "service", List.of("ghost")));
        assertEquals("service.get.service.config.identifier.exception", e.getI18nKey());
        assertEquals("ghost", e.getArgs()[0]);
        verify(port, never()).readConfigMaps(anyString(), any());
    }

    @Test
    void missingDeploymentInSoftwareBranchIsSkipped() {
        // software 分支用**不抛**的变体：读不到 Deployment 只记日志
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw1").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("ghost"));
        when(port.readDeploymentConfigMapNames("ns", "ghost")).thenReturn(List.of());
        when(port.readConfigMaps("ns", List.of())).thenReturn(List.of());

        assertTrue(service().collectConfigMapsForExport("ns", "software", List.of("sw1")).isEmpty());
        verify(port, never()).readDeploymentConfigMapNamesOrThrow(anyString(), anyString());
    }

    @Test
    void noKeysOrUnknownTypeYieldsEmptyList() {
        when(port.readConfigMaps("ns", List.of())).thenReturn(List.of());

        assertTrue(service().collectConfigMapsForExport("ns", "software", null).isEmpty());
        assertTrue(service().collectConfigMapsForExport("ns", "software", List.of()).isEmpty());
        assertTrue(service().collectConfigMapsForExport("ns", "other", List.of("x")).isEmpty());
    }

    @Test
    void configMapsThatCannotBeReadAreSkippedByThePort() {
        // 端口层负责"读不到就跳过"，这里验证调用形状（名字原样传下去）
        when(port.readDeploymentConfigMapNamesOrThrow("ns", "dep")).thenReturn(List.of("cm-1", "missing"));
        when(port.readConfigMaps("ns", List.of("cm-1", "missing"))).thenReturn(List.of(cm("cm-1")));

        var result = service().collectConfigMapsForExport("ns", "service", List.of("dep"));

        assertEquals(1, result.size());
        verify(port).readConfigMaps(eq("ns"), eq(List.of("cm-1", "missing")));
    }
}
