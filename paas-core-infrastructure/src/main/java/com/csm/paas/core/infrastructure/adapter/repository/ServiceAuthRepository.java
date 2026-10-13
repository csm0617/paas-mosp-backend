package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import com.csm.paas.core.infrastructure.dao.IServiceAuthDao;
import com.csm.paas.core.infrastructure.dao.po.ServiceAuthPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository 实现：只调 dao，不含业务判断。 */
@Slf4j
@Repository
public class ServiceAuthRepository implements IServiceAuthRepository {

    @Resource
    private IServiceAuthDao serviceAuthDao;

    @Override
    public List<ServiceAuthEntity> queryAll() {
        List<ServiceAuthPO> rows = serviceAuthDao.selectAll();
        log.info("g_service_auth 读到 {} 行", rows.size());
        return rows.stream()
                .map(po -> ServiceAuthEntity.builder()
                        .serviceId(po.getServiceId())
                        .auth(po.getAuth())
                        .authType(po.getAuthType())
                        .authTime(po.getAuthTime())
                        .authInterval(po.getAuthInterval())
                        .build())
                .toList();
    }
}
