package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/service/gaop` 的 keyword 两段式匹配（厂商 `ServiceController:1631-1644` + `filterServiceName:2070`）。
 *
 * <p>厂商规则：
 * <ol>
 *   <li>先按 **deployment 名** `contains(keyword)`（大小写敏感）；</li>
 *   <li>若第一段结果为空，才用兜底 `filterServiceName`：取 pod 模板注解
 *       **`gaop/serviceLabel`**，`contains(keyword)`（大小写敏感）；注解缺失 → 不匹配。</li>
 * </ol>
 * <b>它不是</b>匹配 i18n 名、也不是匹配 `fixedName`——我第一版按"名字/显示名的忽略大小写包含"实现，
 * 是错的（见 `docs/findings/56`）。
 */
class ServiceGaopKeywordTest {

    private final IServiceRuntimePort port = mock(IServiceRuntimePort.class);
    private final IServiceAuthRepository authRepository = mock(IServiceAuthRepository.class);
    private final ISettingRepository settingRepository = mock(ISettingRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceRuntimePort", port);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "serviceAuthRepository", authRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "i18nRepository", settingRepository);
        return impl;
    }

    private static ServiceRuntimeEntity svc(String name, String serviceLabel) {
        // 必须带 system/appName=gaop，否则会被过滤链最后一道合取项丢掉
        return ServiceRuntimeEntity.builder().name(name).appType("gaop-core").appName("gaop")
                .serviceLabel(serviceLabel).build();
    }

    private List<String> names(String keyword) {
        var entity = service().queryGaopServiceList("ns", 1, 20, 1, keyword);
        return entity.getServices().stream().map(ServiceRuntimeEntity::getName).toList();
    }

    @Test
    void firstStageMatchesDeploymentNameCaseSensitively() {
        when(port.queryServices("ns")).thenReturn(List.of(
                svc("gaop-be", null), svc("gaop-web", null), svc("mysql", null)));

        assertEquals(List.of("gaop-be"), names("gaop-be"), "第一段：名字包含即可");
        assertEquals(List.of(), names("GAOP"), "第一段大小写敏感 ⇒ 大写查不到；第二段无注解也不匹配");
    }

    @Test
    void secondStageMatchesServiceLabelAnnotationOnly() {
        // 名字都不含 "前端"，但 gaop-web 的 gaop/serviceLabel = "框架前端"
        when(port.queryServices("ns")).thenReturn(List.of(
                svc("gaop-be", "框架后端"), svc("gaop-web", "框架前端")));

        assertEquals(List.of("gaop-web"), names("前端"), "第一段为空 ⇒ 走兜底：匹配 gaop/serviceLabel");
        assertEquals(List.of("gaop-web"), names("框架前端"), "兜底也是 contains");
    }

    /** 兜底只认 `gaop/serviceLabel`：没有这个注解的服务，哪怕 i18n 名/固定名里含关键词也不该命中。 */
    @Test
    void servicesWithoutAnnotationNeverMatchTheFallback() {
        when(port.queryServices("ns")).thenReturn(List.of(svc("gaop-be", null)));

        assertEquals(List.of(), names("框架后端"), "gaop-be 没有 gaop/serviceLabel 注解 ⇒ 兜底不匹配");
    }
}
