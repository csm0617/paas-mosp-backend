package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 只读领域服务：把运行态服务聚合成列表。
 * <p>注意 {@code runningDeployment} 的语义在厂商侧尚未确认（运行时样本里 9 个服务、5 个 Pod 在跑，
 * 厂商却给 0）。这里先用"就绪副本 ≥ 1"作为工作定义，并把差异记进工单 06 的结论。
 */
@Slf4j
@Service
public class ServiceQueryServiceImpl implements IServiceQueryService {

    @Resource
    private IServiceRuntimePort serviceRuntimePort;

    @Override
    public ServiceListEntity queryServiceList(String namespace) {
        List<ServiceRuntimeEntity> services = serviceRuntimePort.queryServices(namespace);
        int running = (int) services.stream()
                .filter(s -> s.getReadyReplicas() != null && s.getReadyReplicas() > 0)
                .count();
        log.info("查询服务列表 namespace:{} 共 {} 个，就绪 {} 个", namespace, services.size(), running);
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(services.size())
                .count(services.size())
                .services(services)
                .build();
    }
}
