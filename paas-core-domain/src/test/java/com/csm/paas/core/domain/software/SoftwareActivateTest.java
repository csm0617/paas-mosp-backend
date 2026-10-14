package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.licence.model.valobj.SoftwareActivationPayloadVO;
import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/software/software/active`（厂商 `SoftwareService.authSoftware:3228-3277`）。
 * <p>**五种情形已实测对拉一致**（用自造 RSA 密钥对 + 临时 `g_auth` 公钥行，见 `findings/75`）。
 * <p>与 `cancelAuth` 的两处相反：`result` 恒为 **true**；**全部 id 都不存在时返回空数组**（不报错）。
 */
class SoftwareActivateTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);
    private final ILicenceQueryService licenceQueryService = mock(ILicenceQueryService.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort, licenceQueryService);
    }

    private void payload(String machineCode, List<String> keys) {
        when(licenceQueryService.verifySoftwareActivationCode(anyString()))
                .thenReturn(SoftwareActivationPayloadVO.builder()
                        .machineCode(machineCode).serviceIdList(keys).expiresAt(123L).build());
    }

    @Test
    void activatesAndReturnsTrue() {
        payload("MACHINE", List.of("pc-1"));
        when(licenceQueryService.machineCodeForSoftware()).thenReturn("MACHINE");
        when(repository.queryById("pc-1")).thenReturn(Optional.of(
                SoftwareEntity.builder().id("pc-1").key("probeactiveA").build()));

        var results = service().activateSoftware("tok");

        verify(repository).saveSoftwareAuth(org.mockito.ArgumentMatchers.eq("pc-1"), anyLong(),
                org.mockito.ArgumentMatchers.eq("tok"), org.mockito.ArgumentMatchers.eq(123L));
        assertEquals(1, results.size());
        assertTrue(results.get(0).isResult(), "激活的 result 恒为 true（与 cancelAuth 相反）");
    }

    @Test
    void machineCodeMismatchIsRejected() {
        payload("OTHER", List.of("pc-1"));
        when(licenceQueryService.machineCodeForSoftware()).thenReturn("MACHINE");

        assertEquals("auth.machine.code.mismatch",
                assertThrows(WrongArgumentException.class, () -> service().activateSoftware("tok")).getI18nKey());
        verify(repository, never()).saveSoftwareAuth(any(), anyLong(), any(), anyLong());
    }

    /** 全部 id 都不存在 → **空数组**，不报错（与 cancelAuth 的"一个都没取消到→400"相反）。 */
    @Test
    void allUnknownIdsYieldEmptyListNotAnError() {
        payload("MACHINE", List.of("nope"));
        when(licenceQueryService.machineCodeForSoftware()).thenReturn("MACHINE");
        when(repository.queryById("nope")).thenReturn(Optional.empty());

        assertTrue(service().activateSoftware("tok").isEmpty());
    }
}
