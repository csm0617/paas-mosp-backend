package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.language.service.ILanguageQueryService;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/software/relationService/{id}`（厂商 `SoftwareService.softwareRelationServiceList:3064-3158`）。
 * <p>规格见 `docs/findings/120`。锁住：按 app_name 过滤、`mappingInfo` 三键的形状、
 * 授权的三种情形（含**读接口写库**）、i18n 自动名只对 en_US/fr_FR 生效、以及
 * **关联残留时跳过该行**（我们的收紧 B17，厂商会 NPE）。
 */
class SoftwareRelationServiceTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final IServiceAuthRepository serviceAuthRepository = mock(IServiceAuthRepository.class);
    private final ISettingRepository i18nRepository = mock(ISettingRepository.class);
    private final ILanguageQueryService languageQueryService = mock(ILanguageQueryService.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "softwareRepository", softwareRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceAuthRepository", serviceAuthRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "i18nRepository", i18nRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "languageQueryService", languageQueryService);
        when(languageQueryService.queryCurrent())
                .thenReturn(com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity.builder()
                        .language("zh_CN").version("1").build());
        return impl;
    }

    private void softwareExists() {
        when(softwareRepository.queryById("sw1")).thenReturn(Optional.of(SoftwareEntity.builder().id("sw1").build()));
    }

    private static Map<String, Object> serviceSnapshot(List<Map<String, Object>> ports) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("ports", ports);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("spec", spec);
        return snapshot;
    }

    private static Map<String, Object> port(int port, Integer nodePort, String protocol) {
        Map<String, Object> p = new HashMap<>();
        p.put("port", port);
        p.put("nodePort", nodePort);
        p.put("protocol", protocol);
        return p;
    }

    private static ServiceRuntimeEntity item(String name, Map<String, Object> snapshot) {
        return ServiceRuntimeEntity.builder()
                .name(name).namespace("ns").serviceSnapshot(snapshot)
                .deploymentSnapshot(new HashMap<>()).build();
    }

    @Test
    void missingSoftwareIsRejected() {
        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().querySoftwareRelationServices("ns", "nope"));
        assertEquals("software.get.software.service.info.failed", e.getI18nKey());
    }

    @Test
    void noAppNamesYieldsEmptyList() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of());
        assertTrue(service().querySoftwareRelationServices("ns", "sw1").isEmpty());
    }

    @Test
    void filtersByAppNameAndBuildsMappingInfo() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("nps-svc"));
        when(port.queryServices("ns")).thenReturn(List.of(
                item("nps-svc", serviceSnapshot(List.of(port(8080, null, "TCP"), port(9090, 31234, "TCP")))),
                item("other-svc", serviceSnapshot(List.of()))));
        when(port.readServiceSnapshot("ns", "nps-svc-nodeport")).thenReturn(
                serviceSnapshot(List.of(port(9090, 31840, "TCP"))));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.empty());

        var result = service().querySoftwareRelationServices("ns", "sw1");

        assertEquals(1, result.size(), "只留 app_name 命中的那个");
        var mapping = result.get(0).getMappingInfo();
        assertEquals("nps-svc", mapping.getServiceName());
        assertEquals(2, mapping.getIntranetInfo().size(), "每个 port 一条内网信息");
        assertEquals("nps-svc:8080", mapping.getIntranetInfo().get(0).getIntranetPort());
        assertEquals(8080, mapping.getIntranetInfo().get(0).getContainerPort());
        assertEquals(2, mapping.getNodePortInfo().size(), "Service 里 nodePort 非空的 1 条 + nodeport Service 的 1 条");
        assertEquals(31234, mapping.getNodePortInfo().get(0).getNodePort());
        assertEquals(31840, mapping.getNodePortInfo().get(1).getNodePort());
        assertTrue(mapping.getIngressInfo().isEmpty(), "proxy 恒 null ⇒ ingressInfo 恒空");
        assertNull(result.get(0).getServiceAuth(), "没有授权行且 serviceType 不是前台服务 ⇒ null");
    }

    @Test
    void missingWorkloadRowIsSkippedNotFiveHundred() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("ghost", "nps-svc"));
        when(port.queryServices("ns")).thenReturn(List.of(item("nps-svc", serviceSnapshot(List.of()))));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.empty());

        var result = service().querySoftwareRelationServices("ns", "sw1");

        assertEquals(1, result.size(), "ghost 被跳过（厂商此处 NPE ⇒ 500，见 deviations B17）");
        assertEquals("nps-svc", result.get(0).getName());
        verify(port, never()).readServiceSnapshot(anyString(), org.mockito.ArgumentMatchers.eq("ghost-nodeport"));
    }

    @Test
    void workloadWithoutServiceObjectIsRejected() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("no-svc-obj"));
        when(port.queryServices("ns")).thenReturn(List.of(item("no-svc-obj", null)));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().querySoftwareRelationServices("ns", "sw1"));
        assertEquals("service.get.service.info.error", e.getI18nKey(),
                "厂商 `:2997-3000`：Service 对象为 null 时抛这个键");
    }

    @Test
    void frontEndServiceWithoutAuthRowGetsSynthesizedAuthorization() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("nps-svc"));
        var runtime = item("nps-svc", serviceSnapshot(List.of()));
        runtime.setServiceType("前台服务");
        when(port.queryServices("ns")).thenReturn(List.of(runtime));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.empty());

        var result = service().querySoftwareRelationServices("ns", "sw1");

        var auth = result.get(0).getServiceAuth();
        assertEquals(1, auth.getAuth(), "AuthServiceType.AUTHORIZED");
        assertEquals(1, auth.getAuthType());
        assertEquals("nps-svc", auth.getServiceId());
        verify(serviceAuthRepository, never()).updateAuth(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void expiredAuthorizationIsWrittenBack() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("nps-svc"));
        when(port.queryServices("ns")).thenReturn(List.of(item("nps-svc", serviceSnapshot(List.of()))));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.of(
                ServiceAuthEntity.builder().serviceId("nps-svc").auth(1).authType(1)
                        .authTime(System.currentTimeMillis() - 1000).build()));

        var result = service().querySoftwareRelationServices("ns", "sw1");

        assertEquals(2, result.get(0).getServiceAuth().getAuth(), "EXPIRED");
        verify(serviceAuthRepository).updateAuth("nps-svc", 2);
    }

    @Test
    void notYetExpiredAuthorizationIsUntouched() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("nps-svc"));
        when(port.queryServices("ns")).thenReturn(List.of(item("nps-svc", serviceSnapshot(List.of()))));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.of(
                ServiceAuthEntity.builder().serviceId("nps-svc").auth(1).authType(1)
                        .authTime(System.currentTimeMillis() + 60_000).build()));

        var result = service().querySoftwareRelationServices("ns", "sw1");

        assertEquals(1, result.get(0).getServiceAuth().getAuth());
        verify(serviceAuthRepository, never()).updateAuth(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void autoNameOnlyAppliesForEnglishAndFrench() {
        softwareExists();
        when(softwareRepository.queryAppNamesBySoftwareId("sw1")).thenReturn(List.of("nps-svc"));
        Map<String, Object> deployment = deploymentWithLabel("中文名");
        var runtime = item("nps-svc", serviceSnapshot(List.of()));
        runtime.setDeploymentSnapshot(deployment);
        when(port.queryServices("ns")).thenReturn(List.of(runtime));
        when(serviceAuthRepository.queryByServiceId("nps-svc")).thenReturn(Optional.empty());
        when(i18nRepository.queryValue("i18n.service.en_US.nps-svc")).thenReturn(Optional.of("English Name"));

        // zh_CN ⇒ 不查覆盖表，原值不变
        // 注意：`service()` 每次都会把语言 stub 回 zh_CN，所以下面复用同一个 impl
        var impl = service();
        impl.querySoftwareRelationServices("ns", "sw1");
        assertEquals("中文名", labelOf(deployment));

        // en_US ⇒ 换成覆盖值
        when(languageQueryService.queryCurrent())
                .thenReturn(com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity.builder()
                        .language("en_US").version("1").build());
        impl.querySoftwareRelationServices("ns", "sw1");
        assertEquals("English Name", labelOf(deployment));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deploymentWithLabel(String label) {
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("gaop/serviceLabel", label);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("annotations", annotations);
        Map<String, Object> template = new HashMap<>();
        template.put("metadata", metadata);
        Map<String, Object> spec = new HashMap<>();
        spec.put("template", template);
        Map<String, Object> deployment = new HashMap<>();
        deployment.put("spec", spec);
        return deployment;
    }

    @SuppressWarnings("unchecked")
    private static String labelOf(Map<String, Object> deployment) {
        Map<String, Object> spec = (Map<String, Object>) deployment.get("spec");
        Map<String, Object> template = (Map<String, Object>) spec.get("template");
        Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        return (String) annotations.get("gaop/serviceLabel");
    }
}
