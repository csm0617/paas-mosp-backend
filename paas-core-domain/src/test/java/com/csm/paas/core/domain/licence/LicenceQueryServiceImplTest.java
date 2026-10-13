package com.csm.paas.core.domain.licence;

import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.service.impl.LicenceQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 机器码的两条 base64 拼法（离线）。厂商有**两个**方法，只差一个尾逗号：
 * <ul>
 *   <li>{@code getMachineCodeBase64()}（`/v1/licence`）→ `Base64(原始码 + "," + "gaop" + ",")`；</li>
 *   <li>{@code getMachineCodeBase64ByService()}`（软件详情的 licence.code 前缀）→ `Base64(原始码 + "," + "gaop")`。</li>
 * </ul>
 * 实测（2026-10-14）：两侧逐字节一致，见 `docs/findings/41` 的更正节。
 */
class LicenceQueryServiceImplTest {

    private static final String RAW = "420DF971-09EC-BA4A-BE74-B4868EB29A53";

    private final ILicenceRepository repository = mock(ILicenceRepository.class);

    @Test
    void machineCodeHasTrailingComma() {
        when(repository.rawMachineCode()).thenReturn(RAW);

        String code = new LicenceQueryServiceImpl(repository).machineCode();

        assertEquals(RAW + ",gaop,", new String(Base64.getDecoder().decode(code), StandardCharsets.US_ASCII));
    }

    @Test
    void softwareMachineCodeHasNoTrailingComma() {
        when(repository.rawMachineCode()).thenReturn(RAW);

        String code = new LicenceQueryServiceImpl(repository).machineCodeForSoftware();

        assertEquals(RAW + ",gaop", new String(Base64.getDecoder().decode(code), StandardCharsets.US_ASCII));
    }
}
