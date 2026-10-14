package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/software/existConfigmap`（厂商 `SoftwareService.existConfigMapBySoftwareOrService:3834-3913`）。
 * <p>规格见 `docs/findings/133`。重点：`software` 走"软件 id → 关联服务"、`service` 直接按 Deployment 名；
 * **只要有一个 key 收集到名字就 true**；没有 key ⇒ false（我们的收紧 B22，厂商此处 NPE）。
 */
class SoftwareExistConfigMapTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        return impl;
    }

    @Test
    void softwareTypeWalksItsRelatedServices() {
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw1").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("svc-a", "svc-b"));
        when(port.readDeploymentConfigMapNames("ns", "svc-a")).thenReturn(List.of());
        when(port.readDeploymentConfigMapNames("ns", "svc-b")).thenReturn(List.of("cm-1"));

        assertTrue(service().existConfigMap("ns", "software", List.of("sw1")));
    }

    @Test
    void softwareTypeReturnsFalseWhenNoRelatedServiceHasConfigMap() {
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw1").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("svc-a"));
        when(port.readDeploymentConfigMapNames("ns", "svc-a")).thenReturn(List.of());

        assertFalse(service().existConfigMap("ns", "software", List.of("sw1")));
    }

    @Test
    void softwareTypeSkipsUnknownIdsAndKeepsScanning() {
        when(softwareRepository.queryById("ghost")).thenReturn(Optional.empty());
        when(softwareRepository.queryById("sw2")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw2").build()));
        when(softwareRepository.queryAppNamesBySoftwareId("sw2")).thenReturn(List.of("svc"));
        when(port.readDeploymentConfigMapNames("ns", "svc")).thenReturn(List.of("cm"));

        assertTrue(service().existConfigMap("ns", "software", List.of("ghost", "sw2")));
    }

    @Test
    void serviceTypeReadsDeploymentDirectly() {
        when(port.readDeploymentConfigMapNames("ns", "dep-1")).thenReturn(List.of("cm"));
        assertTrue(service().existConfigMap("ns", "service", List.of("dep-1")));

        when(port.readDeploymentConfigMapNames("ns", "dep-2")).thenReturn(List.of());
        assertFalse(service().existConfigMap("ns", "service", List.of("dep-2")));
    }

    @Test
    void nullOrEmptyKeyReturnsFalseWithoutTouchingCluster() {
        // 厂商：`key != null || key.size() != 0` ⇒ key 为 null 时 NPE（500）；我们按 false（B22）
        assertFalse(service().existConfigMap("ns", "software", null));
        assertFalse(service().existConfigMap("ns", "software", List.of()));
        assertFalse(service().existConfigMap("ns", "service", null));
        verify(port, never()).readDeploymentConfigMapNames(anyString(), anyString());
    }

    @Test
    void unknownTypeReturnsFalse() {
        assertFalse(service().existConfigMap("ns", "other", List.of("x")));
        verify(port, never()).readDeploymentConfigMapNames(eq("ns"), anyString());
    }
}
