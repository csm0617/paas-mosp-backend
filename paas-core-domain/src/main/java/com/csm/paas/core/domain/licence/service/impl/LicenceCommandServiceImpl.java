package com.csm.paas.core.domain.licence.service.impl;

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

    private final IUserSessionRepository userSessionRepository;

    @Override
    public boolean loginOutAllUsers() {
        userSessionRepository.deleteAllSessions();
        return true;
    }
}
