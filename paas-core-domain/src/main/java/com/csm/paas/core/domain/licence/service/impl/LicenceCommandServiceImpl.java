package com.csm.paas.core.domain.licence.service.impl;

import com.csm.paas.core.domain.licence.adapter.port.ILicencePayloadPort;
import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.service.ILicenceCommandService;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 授权/许可写操作（规格见 `docs/findings/150`）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenceCommandServiceImpl implements ILicenceCommandService {

    /** 厂商 `updateFrameworkRsaKey` 里查的是 `name = "gaop"` 那一行。 */
    private static final String ACTIVATE_NAME = "gaop";

    private final IUserSessionRepository userSessionRepository;
    private final ILicenceRepository licenceRepository;
    private final ILicencePayloadPort licencePayloadPort;

    @Override
    public boolean updateFrameworkRsaKey(String encryptInfo) {
        if (encryptInfo == null) {
            throw new com.csm.paas.core.types.exception.WrongArgumentException("licence.new.public.key.empty");
        }
        String publicKey = licencePayloadPort.decryptPublicKey(encryptInfo);
        return licenceRepository.updateActivatePublicKey(ACTIVATE_NAME, publicKey, System.currentTimeMillis()) > 0;
    }

    @Override
    public boolean loginOutAllUsers() {
        userSessionRepository.deleteAllSessions();
        return true;
    }
}
