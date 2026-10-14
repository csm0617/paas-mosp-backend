package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.UpdateServiceVolumesVO;
import com.csm.paas.core.domain.software.model.valobj.VolumeMountVO;
import com.csm.paas.core.domain.software.model.valobj.WorkloadVolumeVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/service/{service}/updateVolumes`（厂商 `ServiceController.updateServiceVolumes:818-947`）。
 * <p>规格见 `docs/findings/119`。重点锁住四处怪点：`items[].path` 用 **key**（挂载路径才是 `dir + "/" + key`）、
 * hostPath 的 `type` 是**空串**、`readOnly == 2` 才置 true、卷名带 5 位随机字符（localtime 除外）。
 */
class ServiceUpdateVolumesTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        when(port.replaceDeploymentVolumes(anyString(), anyString(), any(), any())).thenReturn(Map.of("kind", "Deployment"));
        return impl;
    }

    private static UpdateServiceVolumesVO.ConfigMapItemVO configMap(String configmap, String dir, String... keys) {
        return UpdateServiceVolumesVO.ConfigMapItemVO.builder()
                .configmap(configmap).dir(dir).keys(List.of(keys)).build();
    }

    private static UpdateServiceVolumesVO.HostPathItemVO hostPath(String hostPath, String podPath, Integer readOnly) {
        return UpdateServiceVolumesVO.HostPathItemVO.builder()
                .hostPath(hostPath).podPath(podPath).readOnly(readOnly).build();
    }

    /** 捕获传给端口的卷列表（Mockito 的原始 `List` captor 不能直接参数化，所以在这里转一次）。 */
    @SuppressWarnings("unchecked")
    private List<WorkloadVolumeVO> volumes() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(port).replaceDeploymentVolumes(eq("ns"), eq("nps-svc"), captor.capture(), any());
        return (List<WorkloadVolumeVO>) captor.getValue();
    }

    /** 捕获传给端口的挂载列表。 */
    @SuppressWarnings("unchecked")
    private List<VolumeMountVO> mounts() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(port).replaceDeploymentVolumes(eq("ns"), eq("nps-svc"), any(), captor.capture());
        return (List<VolumeMountVO>) captor.getValue();
    }

    @Test
    void configMapItemPathIsKeyWhileMountPathIsDirPlusKey() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder()
                .configMapList(List.of(configMap("app-config", "/etc/app", "a.yml", "b.yml"))).build());

        var volume = volumes().get(0);
        assertEquals("app-config", volume.getConfigMapName());
        assertEquals("a.yml", volume.getItems().get(0).getKey());
        assertEquals("a.yml", volume.getItems().get(0).getPath(), "items[].path 用的是 key，不是 dir");
        var mount = mounts().get(0);
        assertEquals("/etc/app/a.yml", mount.getMountPath());
        assertEquals("a.yml", mount.getSubPath());
        assertEquals(volume.getName(), mount.getName());
    }

    @Test
    void sameConfigMapNameMergesIntoOneVolumeWithEachDirKept() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder()
                .configMapList(List.of(
                        configMap("app-config", "/etc/a", "a.yml"),
                        configMap("app-config", "/etc/b", "b.yml"),
                        configMap("other", "/etc/c", "c.yml"))).build());

        var list = volumes();
        assertEquals(2, list.size(), "同名 configmap 合成一个卷");
        assertEquals(2, list.get(0).getItems().size());
        var mountList = mounts();
        assertEquals("/etc/a/a.yml", mountList.get(0).getMountPath());
        assertEquals("/etc/b/b.yml", mountList.get(1).getMountPath());
        assertEquals("/etc/c/c.yml", mountList.get(2).getMountPath());
    }

    @Test
    void hostPathTypeIsEmptyStringAndReadOnlyOnlyWhenTwo() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder()
                .hostPathList(List.of(
                        hostPath("/data/logs", "/var/log", 2),
                        hostPath("/data/other", "/var/other", 1),
                        hostPath("/data/none", "/var/none", null))).build());

        var list = volumes();
        assertEquals("", list.get(0).getHostPathType(), "厂商的 type 是空串（不是 null）");
        assertEquals("/data/logs", list.get(0).getHostPath());
        var mountList = mounts();
        assertEquals(Boolean.TRUE, mountList.get(0).getReadOnly(), "readOnly == 2 ⇒ true");
        assertNull(mountList.get(1).getReadOnly(), "readOnly == 1 ⇒ 连 false 都不写");
        assertNull(mountList.get(2).getReadOnly(), "readOnly 为 null ⇒ 我们按非只读处理（deviations B15）");
        assertEquals("/var/log", mountList.get(0).getMountPath());
        assertNull(mountList.get(0).getSubPath(), "hostPath 卷没有 subPath");
    }

    @Test
    void localtimeNamesAreFixedButOthersAreRandomFiveChars() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder()
                .configMapList(List.of(configMap("tenxcloud-time-localtime", "/etc", "localtime")))
                .hostPathList(List.of(hostPath("/etc/localtime", "/etc/localtime", 0),
                        hostPath("/data/x", "/var/x", 0))).build());

        var list = volumes();
        assertEquals("tenxcloud-time-localtime", list.get(0).getName());
        assertEquals("tenxcloud-time-localtime", list.get(1).getName(), "hostPath=/etc/localtime 也是固定名");
        assertTrue(list.get(2).getName().matches("hostpath-volume-[a-z0-9]{5}"),
                "其余是 hostpath-volume- + 5 位 [a-z0-9]：" + list.get(2).getName());
    }

    @Test
    void configMapVolumeNamePrefix() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder()
                .configMapList(List.of(configMap("app-config", "/etc", "a"))).build());

        assertTrue(volumes().get(0).getName()
                .matches("configmap-volume-[a-z0-9]{5}"));
    }

    @Test
    void emptyFormStillReplacesWithEmptyLists() {
        service().updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder().build());

        assertTrue(volumes().isEmpty(), "整份替换 ⇒ 已有卷会被清空");
        assertTrue(mounts().isEmpty());
    }

    @Test
    void missingDeploymentYieldsServiceNameNotFound() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(false);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> impl.updateServiceVolumes("ns", "nps-svc", UpdateServiceVolumesVO.builder().build()));
        assertEquals("service.service.name.not.found", e.getI18nKey());
        assertEquals("nps-svc", e.getArgs()[0], "文案里的 {0} 是服务名");
    }

    @Test
    void buildFailureYieldsFileMappingOrMountFailed() {
        // configMapList 里的 keys 为 null ⇒ 组装阶段 NPE ⇒ 500（照抄厂商的 catch (Exception)）
        var bad = UpdateServiceVolumesVO.ConfigMapItemVO.builder()
                .configmap("app-config").dir("/etc").keys(null).build();
        SystemException e = assertThrows(SystemException.class, () -> service().updateServiceVolumes(
                "ns", "nps-svc", UpdateServiceVolumesVO.builder().configMapList(List.of(bad)).build()));
        assertEquals("service.file.mapping.or.mount.failed", e.getMessage());
    }

    @Test
    void replaceFailureYieldsUpdateConfigFailed() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        when(port.replaceDeploymentVolumes(anyString(), anyString(), any(), any()))
                .thenThrow(new K8sApiException("boom"));

        SystemException e = assertThrows(SystemException.class, () -> impl.updateServiceVolumes(
                "ns", "nps-svc", UpdateServiceVolumesVO.builder().build()));
        assertEquals("service.update.service.config.failed", e.getMessage());
    }

    @Test
    void replaceReturningNullYieldsUpdateConfigFailed() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        when(port.deploymentExists("ns", "nps-svc")).thenReturn(true);
        when(port.replaceDeploymentVolumes(anyString(), anyString(), any(), any())).thenReturn(null);

        SystemException e = assertThrows(SystemException.class, () -> impl.updateServiceVolumes(
                "ns", "nps-svc", UpdateServiceVolumesVO.builder().build()));
        assertEquals("service.update.service.config.failed", e.getMessage());
    }
}
