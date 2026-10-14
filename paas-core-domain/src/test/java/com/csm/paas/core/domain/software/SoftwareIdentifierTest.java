package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 软件申请码的载荷（厂商 `SoftwareService.getSoftwareCode:3166-3186`）。
 * <p>算法完全确定：`{serviceIdList: 入参, machineCode: getMachineCodeBase64ByService()}`；
 * 空列表 → 400 `software.invalid.api.parameter`。
 * <p><b>JSON 组装 + Base64 在 trigger 层</b>（domain 模块没有 Jackson），其逐字节正确性由
 * **实测对拉**覆盖（`findings/73`：两种入参的输出与厂商完全相同）。
 */
class SoftwareIdentifierTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);
    private final ILicenceQueryService licenceQueryService = mock(ILicenceQueryService.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort, licenceQueryService);
    }

    @Test
    void buildsPayloadWithSoftwareMachineCode() {
        when(licenceQueryService.machineCodeForSoftware()).thenReturn("MACHINE");

        var vo = service().generateSoftwareIdentifier(List.of("appA", "appB"));

        assertEquals(List.of("appA", "appB"), vo.getServiceIdList(), "顺序原样保留");
        assertEquals("MACHINE", vo.getMachineCode(), "用的是**软件**机器码（无尾逗号那一版）");
    }

    @Test
    void emptyKeyListIsRejected() {
        assertEquals("software.invalid.api.parameter",
                assertThrows(WrongArgumentException.class, () -> service().generateSoftwareIdentifier(List.of()))
                        .getI18nKey());
        assertEquals("software.invalid.api.parameter",
                assertThrows(WrongArgumentException.class, () -> service().generateSoftwareIdentifier(null))
                        .getI18nKey());
    }
}
