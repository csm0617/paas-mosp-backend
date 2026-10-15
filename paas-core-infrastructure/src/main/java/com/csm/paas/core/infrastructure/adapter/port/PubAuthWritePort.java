package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.node.adapter.port.IPubAuthWritePort;
import com.csm.paas.core.infrastructure.gateway.PubServiceAuthGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Port 实现：只调 {@link PubServiceAuthGateway}。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubAuthWritePort implements IPubAuthWritePort {

    private final PubServiceAuthGateway pubServiceAuthGateway;

    @Override
    public boolean upsertServiceAuth(String serviceId, Integer auth, Integer authType, Long authTime,
                                     String authCode) {
        return pubServiceAuthGateway.upsertServiceAuth(serviceId, auth, authType, authTime, authCode);
    }
}
