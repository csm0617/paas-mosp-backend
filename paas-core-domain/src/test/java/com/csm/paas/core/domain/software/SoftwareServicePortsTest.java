package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ServicePortVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/software/{softwareId}/service/{serviceName}`（厂商 `SoftwareService.servicePortList:2304-2306`）。
 * <p>两个照抄点：查询条件只有 `software_id` + `service_name` 两个等值；**查不到返回空数组**（不报错）。
 */
class SoftwareServicePortsTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);

    @Test
    void passesBothKeysThroughAndReturnsWhatRepositoryGives() {
        when(repository.queryServicePorts("sw", "svc")).thenReturn(List.of(
                ServicePortVO.builder().id(1L).softwareId("sw").serviceName("svc").port(8080).build()));

        var result = new SoftwareQueryServiceImpl(repository, webEntryStatusPort).queryServicePorts("sw", "svc");

        verify(repository).queryServicePorts("sw", "svc");
        assertEquals(1, result.size());
        assertEquals(8080, result.get(0).getPort());
    }

    @Test
    void missingCombinationYieldsEmptyListNotAnError() {
        when(repository.queryServicePorts("sw", "nope")).thenReturn(List.of());

        assertTrue(new SoftwareQueryServiceImpl(repository, webEntryStatusPort)
                .queryServicePorts("sw", "nope").isEmpty());
    }
}
