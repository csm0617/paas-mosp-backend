package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.model.valobj.WorkloadRefVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 服务详情与 updateServiceRole 的离线契约（厂商 `ServiceController:1404-1531`）。
 *
 * <p>规格见 `IServiceQueryService.queryServiceDetail` / `updateServiceRole`；
 * 两处照抄点：① 详情端点的 `nameUs`/`nameFr` 是 **写死 en_US/fr_FR** 的 i18n 查询 + 注解回退；
 * ② `updateServiceRole` 里那个"put 被立刻覆盖"的缺陷。
 */
class ServiceDetailAndRoleTest {

    private static final String NS = "gaop-namespace";

    private final IServiceRuntimePort runtimePort = mock(IServiceRuntimePort.class);
    private final ISettingRepository i18nRepository = mock(ISettingRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        ReflectionTestUtils.setField(impl, "serviceRuntimePort", runtimePort);
        ReflectionTestUtils.setField(impl, "i18nRepository", i18nRepository);
        return impl;
    }

    // ---------- GET /v1/service/{serviceName} ----------

    /** 服务不存在 ⇒ 400 `service.service.name.not.found`（带服务名）。 */
    @Test
    void detailMissingServiceThrowsVendorKey() {
        when(runtimePort.lookupServiceDetail(NS, "nope")).thenReturn(null);

        var ex = assertThrows(WrongArgumentException.class, () -> service().queryServiceDetail(NS, "nope"));
        assertEquals("service.service.name.not.found", ex.getI18nKey());
        assertEquals("nope", ex.getArgs()[0]);
    }

    /** i18n 有值时用 i18n；**空白则回退**注解 `gaop/serviceLabel`（厂商 `:1476-1479`）。 */
    @Test
    void detailNamesPreferI18nThenFallbackToServiceLabel() {
        var entity = ServiceRuntimeEntity.builder()
                .name("gaop-web")
                .serviceLabel("界面显示名")
                .build();
        when(runtimePort.lookupServiceDetail(NS, "gaop-web")).thenReturn(entity);
        when(i18nRepository.queryValue("i18n.service.en_US.gaop-web")).thenReturn(Optional.of("Web UI"));
        // fr_FR 查不到 ⇒ 回退到 serviceLabel
        when(i18nRepository.queryValue("i18n.service.fr_FR.gaop-web")).thenReturn(Optional.empty());

        var result = service().queryServiceDetail(NS, "gaop-web");

        assertEquals("Web UI", result.getNameUs());
        assertEquals("界面显示名", result.getNameFr());
    }

    /** i18n 查到的值是**空白**时同样回退（厂商 `StringUtils.isBlank`）。 */
    @Test
    void detailBlankI18nFallsBackToServiceLabel() {
        var entity = ServiceRuntimeEntity.builder().name("gaop-web").serviceLabel("L").build();
        when(runtimePort.lookupServiceDetail(NS, "gaop-web")).thenReturn(entity);
        when(i18nRepository.queryValue(anyString())).thenReturn(Optional.of("   "));

        var result = service().queryServiceDetail(NS, "gaop-web");

        assertEquals("L", result.getNameUs());
        assertEquals("L", result.getNameFr());
    }

    /** 详情端点把 `hostIp` / `terminatedSnapshot` / `nodePortServiceSnapshot` 原样带出（端口层负责填）。 */
    @Test
    void detailCarriesSnapshotFieldsFromPort() {
        Map<String, Object> terminated = new LinkedHashMap<>();
        terminated.put("reason", "Error");
        var entity = ServiceRuntimeEntity.builder()
                .name("gaop-web")
                .hostIP("10.0.0.5")
                .terminatedSnapshot(terminated)
                .nodePortServiceSnapshot(Map.of("kind", "Service"))
                .build();
        when(runtimePort.lookupServiceDetail(NS, "gaop-web")).thenReturn(entity);
        when(i18nRepository.queryValue(anyString())).thenReturn(Optional.empty());

        var result = service().queryServiceDetail(NS, "gaop-web");

        assertEquals("10.0.0.5", result.getHostIP());
        assertEquals(terminated, result.getTerminatedSnapshot());
        assertEquals("Service", result.getNodePortServiceSnapshot().get("kind"));
    }

    // ---------- PUT /v1/service/updateServiceRole ----------

    /** 工作负载都不存在 ⇒ 400 `service.get.service.failed.not.found`（不 patch）。 */
    @Test
    void updateServiceRoleMissingWorkloadThrows() {
        when(runtimePort.readWorkloadRef(NS, "svc")).thenReturn(null);

        var ex = assertThrows(WrongArgumentException.class,
                () -> service().updateServiceRole(NS, "other-svc", "svc"));
        assertEquals("service.get.service.failed.not.found", ex.getI18nKey());
        verify(runtimePort, never()).patchDeploymentTemplateMetadata(anyString(), anyString(), any());
    }

    /**
     * **照抄的厂商缺陷**：工作负载已有 annotations（非 null）时，
     * `gaop/serviceRole` 那个 put 被立即覆盖丢弃 ⇒ patch 出去的注解里**没有** serviceRole。
     */
    @Test
    void updateServiceRoleDropsServiceRoleWhenAnnotationsExist() {
        when(runtimePort.readWorkloadRef(NS, "svc")).thenReturn(WorkloadRefVO.builder().deployment(true).name("svc").build());
        Map<String, String> existing = new LinkedHashMap<>();
        existing.put("gaop/serviceLabel", "旧名字");
        when(runtimePort.readWorkloadTemplateAnnotations(NS, "svc")).thenReturn(existing);

        assertTrue(service().updateServiceRole(NS, "other-svc", "svc"));

        verify(runtimePort).patchDeploymentTemplateMetadata(eq(NS), eq("svc"), eq(existing));
        assertFalse(existing.containsKey("gaop/serviceRole"), "厂商这里 put 被覆盖，不该出现 serviceRole");
    }

    /** annotations 原本为 **null** 时，那个 HashMap（含 serviceRole）才会被用上。 */
    @Test
    void updateServiceRoleWritesServiceRoleWhenAnnotationsNull() {
        when(runtimePort.readWorkloadRef(NS, "svc")).thenReturn(WorkloadRefVO.builder().deployment(true).name("svc").build());
        when(runtimePort.readWorkloadTemplateAnnotations(NS, "svc")).thenReturn(null);

        assertTrue(service().updateServiceRole(NS, "other-svc", "svc"));

        verify(runtimePort).patchDeploymentTemplateMetadata(eq(NS), eq("svc"),
                org.mockito.ArgumentMatchers.argThat(m -> "other-svc".equals(m.get("gaop/serviceRole"))));
    }

    /** Deployment 分支 patch 失败 ⇒ 用**同一份注解**回退 StatefulSet；回退成功仍返回 true。 */
    @Test
    void updateServiceRoleFallsBackToStatefulSet() {
        when(runtimePort.readWorkloadRef(NS, "svc")).thenReturn(WorkloadRefVO.builder().deployment(true).name("svc").build());
        when(runtimePort.readWorkloadTemplateAnnotations(NS, "svc")).thenReturn(null);
        when(runtimePort.patchDeploymentTemplateMetadata(eq(NS), eq("svc"), any()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        when(runtimePort.patchStatefulSetTemplateMetadata(eq(NS), eq("svc"), any())).thenReturn(true);

        assertTrue(service().updateServiceRole(NS, "k", "svc"));
        verify(runtimePort).patchStatefulSetTemplateMetadata(eq(NS), eq("svc"), any());
    }

    /** 两个分支都失败 ⇒ 400 `service.update.deployment.failed`。 */
    @Test
    void updateServiceRoleBothBranchesFail() {
        when(runtimePort.readWorkloadRef(NS, "svc")).thenReturn(WorkloadRefVO.builder().deployment(true).name("svc").build());
        when(runtimePort.readWorkloadTemplateAnnotations(NS, "svc")).thenReturn(null);
        when(runtimePort.patchDeploymentTemplateMetadata(eq(NS), eq("svc"), any()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom"));
        when(runtimePort.patchStatefulSetTemplateMetadata(eq(NS), eq("svc"), any()))
                .thenThrow(new com.csm.paas.core.types.exception.K8sApiException("boom2"));

        var ex = assertThrows(WrongArgumentException.class, () -> service().updateServiceRole(NS, "k", "svc"));
        assertEquals("service.update.deployment.failed", ex.getI18nKey());
    }

    /** StatefulSet 工作负载走 StatefulSet 分支（不碰 Deployment 的 patch）。 */
    @Test
    void updateServiceRoleStatefulSetBranch() {
        when(runtimePort.readWorkloadRef(NS, "sts")).thenReturn(WorkloadRefVO.builder().deployment(false).name("sts").build());
        when(runtimePort.readWorkloadTemplateAnnotations(NS, "sts")).thenReturn(null);

        assertTrue(service().updateServiceRole(NS, "k", "sts"));

        verify(runtimePort).patchStatefulSetTemplateMetadata(eq(NS), eq("sts"), any());
        verify(runtimePort, never()).patchDeploymentTemplateMetadata(anyString(), anyString(), any());
    }

    // ---------- GET /v1/service/imageStorage（类 C，解析规则离线可测）----------

    /** `df -h` 的 `T`/`G`/`M` 三种后缀各自换算；两个字段都是**字节**。 */
    @Test
    void imageStorageParsesDfOutput() {
        var impl = service();
        var ssh = mock(com.csm.paas.core.domain.remote.adapter.port.ISshPort.class);
        ReflectionTestUtils.setField(impl, "sshPort", ssh);
        when(ssh.exec(" df -h /data/paas/ | awk 'NR==2 {print $2, $4}'")).thenReturn("100G 20G\n");

        var info = impl.queryImageStorage("/data/paas/");

        assertEquals(100L * 1024 * 1024 * 1024, info.getTotal());
        assertEquals(20L * 1024 * 1024 * 1024, info.getFree());
    }

    /** SSH 抛异常 ⇒ `{free:0,total:0}`（厂商不报错）。 */
    @Test
    void imageStorageSshFailureYieldsZeros() {
        var impl = service();
        var ssh = mock(com.csm.paas.core.domain.remote.adapter.port.ISshPort.class);
        ReflectionTestUtils.setField(impl, "sshPort", ssh);
        when(ssh.exec(anyString())).thenThrow(new IllegalStateException("no ssh"));

        var info = impl.queryImageStorage("/data/paas/");

        assertEquals(0L, info.getTotal());
        assertEquals(0L, info.getFree());
    }

    /** 没有 `K`/字节 分支：`df` 给出不带后缀的数值时结果是 **0**（厂商 `switchSize` 照抄）。 */
    @Test
    void imageStorageWithoutUnitSuffixYieldsZero() {
        var impl = service();
        var ssh = mock(com.csm.paas.core.domain.remote.adapter.port.ISshPort.class);
        ReflectionTestUtils.setField(impl, "sshPort", ssh);
        when(ssh.exec(anyString())).thenReturn("1048576 524288\n");

        var info = impl.queryImageStorage("/data/paas/");

        assertEquals(0L, info.getTotal());
        assertEquals(0L, info.getFree());
    }

    /** 输出为空 ⇒ 全 0。 */
    @Test
    void imageStorageBlankOutputYieldsZeros() {
        var impl = service();
        var ssh = mock(com.csm.paas.core.domain.remote.adapter.port.ISshPort.class);
        ReflectionTestUtils.setField(impl, "sshPort", ssh);
        when(ssh.exec(anyString())).thenReturn("");

        var info = impl.queryImageStorage("/data/paas/");


        assertEquals(0L, info.getTotal());
    }
}
