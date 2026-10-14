package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.WorkloadStateVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/service/batch-stop` / `batch-start`（厂商 `serviceStop:3649-3685` / `serviceStart:3689-3740`）。
 * <p>规格见 `docs/findings/126`。重点：批量停止看 `spec.replicas`、批量启动看 `status` 推导出的状态串
 * （`" ROLLING_UPDATE"` **带前导空格**、`"STARING"` 是厂商拼写）、以及 Deployment/StatefulSet
 * 两份状态推导的**条件差异**。
 */
class ServiceBatchStartStopTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        return impl;
    }

    private static WorkloadStateVO deployment(Integer specReplicas, Integer replicas, Integer ready,
                                              Integer available, Integer updated, Integer unavailable) {
        return WorkloadStateVO.builder().deployment(true).specReplicas(specReplicas).replicas(replicas)
                .readyReplicas(ready).availableReplicas(available).updatedReplicas(updated)
                .unavailableReplicas(unavailable).build();
    }

    // ---------- batch-stop ----------

    @Test
    void stopScalesDownOnlyWhenNotAlreadyZero() {
        when(port.readWorkloadState("ns", "run")).thenReturn(deployment(2, 2, 2, 2, 2, 0));
        when(port.readWorkloadState("ns", "stopped")).thenReturn(deployment(0, 0, 0, null, 0, null));

        assertTrue(service().batchStopServices("ns", List.of("run", "stopped")));

        verify(port).replaceWorkloadReplicas("ns", "run", true, 0);
        verify(port, never()).replaceWorkloadReplicas(eq("ns"), eq("stopped"), anyBoolean(), anyInt());
    }

    @Test
    void stopSkipsUnknownWorkloadsAndEmptyInput() {
        when(port.readWorkloadState("ns", "ghost")).thenReturn(null);

        assertTrue(service().batchStopServices("ns", List.of("ghost")));
        assertTrue(service().batchStopServices("ns", List.of()));
        assertTrue(service().batchStopServices("ns", null));
        verify(port, never()).replaceWorkloadReplicas(anyString(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void stopFailureYieldsOperationFailedWithChineseVerb() {
        when(port.readWorkloadState("ns", "run")).thenReturn(deployment(1, 1, 1, 1, 1, 0));
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).replaceWorkloadReplicas(anyString(), anyString(), anyBoolean(), anyInt());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().batchStopServices("ns", List.of("run")));
        assertEquals("service.operation.failed", e.getI18nKey());
        assertEquals("停止", e.getArgs()[0]);
    }

    // ---------- batch-start ----------

    @Test
    void startScalesUpWhenStatusIsStop() {
        when(port.readWorkloadState("ns", "stopped")).thenReturn(deployment(0, 0, 0, null, 0, null));

        assertTrue(service().batchStartServices("ns", List.of("stopped")));
        verify(port).replaceWorkloadReplicas("ns", "stopped", true, 1);
    }

    @Test
    void startSkipsRunningRollingAndStaring() {
        // RUNNING：available/ready/replicas/updated 都非空且 ready <= available
        when(port.readWorkloadState("ns", "running")).thenReturn(deployment(1, 1, 1, 1, 1, 0));
        //  ROLLING_UPDATE：ready > available（且 unavailable 非空）
        when(port.readWorkloadState("ns", "rolling")).thenReturn(deployment(2, 2, 3, 2, 2, 1));
        // STARING：available 为空，但 replicas/updated/unavailable 非空
        when(port.readWorkloadState("ns", "staring")).thenReturn(deployment(1, 1, null, null, 1, 1));

        assertTrue(service().batchStartServices("ns", List.of("running", "rolling", "staring")));
        verify(port, never()).replaceWorkloadReplicas(anyString(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void deploymentRollingRequiresUnavailableNonNullWhileStatefulSetDoesNot() {
        // Deployment：available/ready/replicas/updated 都有、ready > available，但 unavailable 为 null
        // ⇒ 厂商 Deployment 那份判不出 ROLLING_UPDATE ⇒ 落到 RUNNING ⇒ 不启动
        when(port.readWorkloadState("ns", "dep")).thenReturn(deployment(2, 2, 3, 2, 2, null));
        // StatefulSet：同样形状（没有 unavailable 字段）⇒ 判出 " ROLLING_UPDATE" ⇒ 也不启动
        when(port.readWorkloadState("ns", "sts")).thenReturn(WorkloadStateVO.builder()
                .deployment(false).specReplicas(2).replicas(2).readyReplicas(3).availableReplicas(2)
                .updatedReplicas(2).build());

        assertTrue(service().batchStartServices("ns", List.of("dep", "sts")));
        verify(port, never()).replaceWorkloadReplicas(anyString(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void statefulSetWithoutStatusFieldsIsStop() {
        // StatefulSet：status 全空 ⇒ STOP ⇒ 启动
        when(port.readWorkloadState("ns", "sts")).thenReturn(WorkloadStateVO.builder()
                .deployment(false).specReplicas(0).build());

        assertTrue(service().batchStartServices("ns", List.of("sts")));
        verify(port).replaceWorkloadReplicas("ns", "sts", false, 1);
    }

    @Test
    void startFailureYieldsOperationFailedWithChineseVerb() {
        when(port.readWorkloadState("ns", "stopped")).thenReturn(deployment(0, 0, 0, null, 0, null));
        Mockito.doThrow(new K8sApiException("boom"))
                .when(port).replaceWorkloadReplicas(anyString(), anyString(), anyBoolean(), anyInt());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().batchStartServices("ns", List.of("stopped")));
        assertEquals("service.operation.failed", e.getI18nKey());
        assertEquals("启动", e.getArgs()[0]);
    }
}
