package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ServicePodRefVO;
import com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/batch-restart`（厂商 `ServiceController.serviceRestart:3826-3912`）。
 * <p>规格见 `docs/findings/127`。锁住：Deployment 分支**先写 `g_pod_event` 再 replace**、
 * `restartNum = max(1, 历史最大值)`、StatefulSet 分支**不写库**、注解值是 `LocalDateTime.now().toString()`、
 * 以及两个错误键的分工。
 */
class ServiceBatchRestartTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        return impl;
    }

    private void deploymentExists() {
        when(port.readWorkloadState("ns", "svc")).thenReturn(
                WorkloadStateVO.builder().deployment(true).specReplicas(1).build());
    }

    private void podNamed(String name) {
        when(port.listPodsByLabel("ns", "system/svcName", "svc")).thenReturn(
                List.of(ServicePodRefVO.builder().name(name).build()));
    }

    @Test
    void deploymentBranchWritesPodEventThenRestarts() {
        deploymentExists();
        podNamed("svc-abc-1");
        when(softwareRepository.queryPodEventRestartCounts("svc", "svc-abc-1")).thenReturn(List.of(3, 7));

        assertTrue(service().batchRestartServices("ns", List.of("svc")));

        // restartNum = max(1, 7) = 7
        verify(softwareRepository).insertPodEvent(eq("svc"), eq("svc-abc-1"), anyLong(), eq(7));
        var at = ArgumentCaptor.forClass(String.class);
        verify(port).replaceWorkloadRestartedAt(eq("ns"), eq("svc"), eq(true), at.capture());
        assertTrue(at.getValue().startsWith("20"), "注解值是 LocalDateTime.now().toString()：" + at.getValue());
    }

    @Test
    void restartNumIsAtLeastOneWhenNoHistory() {
        deploymentExists();
        podNamed("svc-abc-1");
        when(softwareRepository.queryPodEventRestartCounts("svc", "svc-abc-1")).thenReturn(List.of());

        service().batchRestartServices("ns", List.of("svc"));

        verify(softwareRepository).insertPodEvent(eq("svc"), eq("svc-abc-1"), anyLong(), eq(1));
    }

    @Test
    void noPodMeansNoDbWriteButStillRestarts() {
        deploymentExists();
        when(port.listPodsByLabel("ns", "system/svcName", "svc")).thenReturn(List.of());

        assertTrue(service().batchRestartServices("ns", List.of("svc")));

        verify(softwareRepository, never()).insertPodEvent(anyString(), anyString(), anyLong(), anyInt());
        verify(port).replaceWorkloadRestartedAt(eq("ns"), eq("svc"), eq(true), anyString());
    }

    @Test
    void statefulSetBranchNeverWritesDb() {
        when(port.readWorkloadState("ns", "svc")).thenReturn(
                WorkloadStateVO.builder().deployment(false).specReplicas(1).build());

        assertTrue(service().batchRestartServices("ns", List.of("svc")));

        verify(port, never()).listPodsByLabel(anyString(), anyString(), anyString());
        verify(softwareRepository, never()).insertPodEvent(anyString(), anyString(), anyLong(), anyInt());
        verify(port).replaceWorkloadRestartedAt(eq("ns"), eq("svc"), eq(false), anyString());
    }

    @Test
    void unknownWorkloadAndEmptyInputAreSkipped() {
        when(port.readWorkloadState("ns", "ghost")).thenReturn(null);

        assertTrue(service().batchRestartServices("ns", List.of("ghost")));
        assertTrue(service().batchRestartServices("ns", List.of()));
        assertTrue(service().batchRestartServices("ns", null));
        verify(port, never()).replaceWorkloadRestartedAt(anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void podLookupFailureYieldsServiceInfoException() {
        deploymentExists();
        when(port.listPodsByLabel(anyString(), anyString(), anyString()))
                .thenThrow(new K8sApiException("boom"));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().batchRestartServices("ns", List.of("svc")));
        assertEquals("service.get.service.info.exception", e.getI18nKey());
    }

    @Test
    void replaceFailureYieldsRestartNameFailed() {
        deploymentExists();
        podNamed("svc-abc-1");
        when(softwareRepository.queryPodEventRestartCounts("svc", "svc-abc-1")).thenReturn(List.of());
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).replaceWorkloadRestartedAt(anyString(), anyString(), anyBoolean(), anyString());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().batchRestartServices("ns", List.of("svc")));
        assertEquals("service.restart.name.failed", e.getI18nKey());
        assertEquals("svc", e.getArgs()[0]);
        // 厂商顺序：写库在前 ⇒ replace 失败时那条记录**已经落库**（照抄）
        verify(softwareRepository).insertPodEvent(eq("svc"), eq("svc-abc-1"), anyLong(), eq(1));
    }
}
