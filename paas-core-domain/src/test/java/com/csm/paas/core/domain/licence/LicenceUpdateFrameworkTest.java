package com.csm.paas.core.domain.licence;

import com.csm.paas.core.domain.licence.adapter.port.ILicencePayloadPort;
import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.service.impl.LicenceCommandServiceImpl;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/licence/updateFramework`（厂商 `LicenceService.updateFrameworkRsaKey:1078-1107`）。
 * <p>规格见 `docs/findings/151`。
 */
class LicenceUpdateFrameworkTest {

    private final IUserSessionRepository userSessionRepository = mock(IUserSessionRepository.class);
    private final ILicenceRepository licenceRepository = mock(ILicenceRepository.class);
    private final ILicencePayloadPort payloadPort = mock(ILicencePayloadPort.class);

    private LicenceCommandServiceImpl service() {
        return new LicenceCommandServiceImpl(userSessionRepository, licenceRepository, payloadPort);
    }

    @Test
    void nullEncryptInfoIsRejected() {
        var e = assertThrows(WrongArgumentException.class, () -> service().updateFrameworkRsaKey(null));
        assertEquals("licence.new.public.key.empty", e.getI18nKey());
    }

    @Test
    void decryptedKeyIsWrittenToTheGaopActivateRow() {
        when(payloadPort.decryptPublicKey("cipher")).thenReturn("PUBKEY");
        when(licenceRepository.updateActivatePublicKey(eq("gaop"), eq("PUBKEY"), anyLong())).thenReturn(1);

        assertTrue(service().updateFrameworkRsaKey("cipher"));

        verify(licenceRepository).updateActivatePublicKey(eq("gaop"), eq("PUBKEY"), anyLong());
    }

    @Test
    void zeroAffectedRowsIsFalse() {
        when(payloadPort.decryptPublicKey("cipher")).thenReturn("PUBKEY");
        when(licenceRepository.updateActivatePublicKey(eq("gaop"), eq("PUBKEY"), anyLong())).thenReturn(0);

        assertFalse(service().updateFrameworkRsaKey("cipher"));
    }

    @Test
    void decryptFailurePropagates() {
        when(payloadPort.decryptPublicKey("bad"))
                .thenThrow(new WrongArgumentException("licence.parse.authorization.info.failed"));

        var e = assertThrows(WrongArgumentException.class, () -> service().updateFrameworkRsaKey("bad"));
        assertEquals("licence.parse.authorization.info.failed", e.getI18nKey());
    }
}
