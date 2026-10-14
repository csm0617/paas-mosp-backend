package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.PodRestartInfoVO;
import com.csm.paas.core.domain.software.service.impl.ServiceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/service/restartInfo?serviceName=`（厂商 `ServiceController:3950-3965`）。
 * <p>**三种情形已实测对拉一致**（`findings/81`）：有记录、另一服务、不存在的服务（→ 空数组）。
 */
class ServiceRestartInfoTest {

    private final ISoftwareRepository podEventRepository = mock(ISoftwareRepository.class);

    private ServiceQueryServiceImpl service() {
        var impl = new ServiceQueryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "podEventRepository", podEventRepository);
        return impl;
    }

    @Test
    void returnsRowsFromRepositoryAsIs() {
        when(podEventRepository.queryPodRestartInfo("gaop-web")).thenReturn(List.of(
                PodRestartInfoVO.builder().podName("gaop-web-96bbccb46-7rs5d")
                        .createTime(1791907176318L).restartCount(1).build()));

        var list = service().queryRestartInfo("gaop-web");

        verify(podEventRepository).queryPodRestartInfo("gaop-web");
        assertEquals(1, list.size());
        assertEquals("gaop-web-96bbccb46-7rs5d", list.get(0).getPodName());
    }

    /** 没有记录 → **空数组**（不报错、不是 404）。 */
    @Test
    void unknownServiceYieldsEmptyList() {
        when(podEventRepository.queryPodRestartInfo("no-such-svc")).thenReturn(List.of());

        assertTrue(service().queryRestartInfo("no-such-svc").isEmpty());
    }
}
