package com.csm.paas.core.domain.licence;

import com.csm.paas.core.domain.licence.adapter.port.IGisLicenseStatusPort;
import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.service.impl.LicenceQueryServiceImpl;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/licence/gisStatus`（厂商 `LicenceService.getGisActiveStatus:1151-1205`）。
 * <p>规格见 `docs/findings/152`。重点：**读不到 Deployment / 状态不满足条件 ⇒ 2 且不打 GIS 接口**；
 * 满足条件才请求 GIS；请求失败也 ⇒ 2。
 */
class LicenceGisStatusTest {

    private final ILicenceRepository licenceRepository = mock(ILicenceRepository.class);
    private final IServiceRuntimePort serviceRuntimePort = mock(IServiceRuntimePort.class);
    private final IGisLicenseStatusPort gisPort = mock(IGisLicenseStatusPort.class);

    private LicenceQueryServiceImpl service() {
        var impl = new LicenceQueryServiceImpl(licenceRepository, serviceRuntimePort, gisPort);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "gisUrl", "gis-server:8080");
        return impl;
    }

    private static WorkloadStateVO state(Integer replicas, Integer ready, Integer available, Integer updated,
                                        Integer unavailable, boolean deployment) {
        return WorkloadStateVO.builder().deployment(deployment).specReplicas(1)
                .replicas(replicas).readyReplicas(ready).availableReplicas(available)
                .updatedReplicas(updated).unavailableReplicas(unavailable).build();
    }

    @Test
    void missingDeploymentMeansTwoWithoutCallingGis() {
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server")).thenReturn(null);

        assertEquals(2, service().queryGisActiveStatus("ns"));
        verify(gisPort, never()).queryEvServer(anyString());
    }

    @Test
    void statefulSetOnlyIsTreatedAsMissing() {
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server"))
                .thenReturn(state(1, 1, 1, 1, 0, false));

        assertEquals(2, service().queryGisActiveStatus("ns"));
        verify(gisPort, never()).queryEvServer(anyString());
    }

    @Test
    void firstFieldGroupTriggersTheGisCall() {
        // available/ready/replicas/updated 全非空
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server"))
                .thenReturn(state(1, 1, 1, 1, null, true));
        when(gisPort.queryEvServer("http://gis-server:8080")).thenReturn(1);

        assertEquals(1, service().queryGisActiveStatus("ns"));
        verify(gisPort).queryEvServer("http://gis-server:8080");
    }

    @Test
    void secondFieldGroupAlsoTriggersTheGisCall() {
        // available 为空，但 ready/replicas/updated/unavailable 非空
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server"))
                .thenReturn(state(1, 1, null, 1, 0, true));
        when(gisPort.queryEvServer(anyString())).thenReturn(0);

        assertEquals(0, service().queryGisActiveStatus("ns"));
    }

    @Test
    void stateNotMatchingEitherGroupMeansTwo() {
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server"))
                .thenReturn(state(1, null, null, null, null, true));

        assertEquals(2, service().queryGisActiveStatus("ns"));
        verify(gisPort, never()).queryEvServer(anyString());
    }

    @Test
    void gisCallFailureMeansTwo() {
        when(serviceRuntimePort.readWorkloadState("ns", "gis-server"))
                .thenReturn(state(1, 1, 1, 1, 0, true));
        when(gisPort.queryEvServer(anyString()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));

        assertEquals(2, service().queryGisActiveStatus("ns"));
    }

    @Test
    void urlWithoutSchemeGetsHttpPrefix() {
        var impl = new LicenceQueryServiceImpl(licenceRepository, serviceRuntimePort, gisPort);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "gisUrl", "https://gis-x:9443");
        when(serviceRuntimePort.readWorkloadState("ns", "gis-x")).thenReturn(state(1, 1, 1, 1, 0, true));
        when(gisPort.queryEvServer("https://gis-x:9443")).thenReturn(1);

        assertEquals(1, impl.queryGisActiveStatus("ns"));
        // 主机名只取冒号前那段
        verify(serviceRuntimePort).readWorkloadState("ns", "gis-x");
    }
}
