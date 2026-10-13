package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port 实现：只调 gateway，把 K8s 对象翻成领域实体（防腐层）。
 * <p><b>列表是 workload 驱动</b>：厂商按 StatefulSet→Deployment 遍历再找同名 Service
 * （{@code TenxService.java:2307-2342}），没有同名 Service 的 workload **照样入列**（service 为空）；
 * 这正是厂商结果里没有 {@code gaop-mysql}（ExternalName、无工作负载）的原因。
 */
@Slf4j
@Component
public class ServiceRuntimePort implements IServiceRuntimePort {

    /** 厂商默认页大小（{@code page.setSize(10)}）。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Resource
    private ServiceRuntimeGateway serviceRuntimeGateway;

    @Override
    public List<ServiceRuntimeEntity> queryServices(String namespace) {
        Map<String, Service> serviceByName = new LinkedHashMap<>();
        for (Service svc : serviceRuntimeGateway.listServices(namespace)) {
            serviceByName.put(svc.getMetadata().getName(), svc);
        }

        List<ServiceRuntimeEntity> workloads = new ArrayList<>();
        for (StatefulSet ss : serviceRuntimeGateway.listStatefulSets(namespace)) {
            workloads.add(toEntity(namespace, ss.getMetadata().getName(),
                    ss.getSpec() != null ? ss.getSpec().getReplicas() : null,
                    serviceByName.get(ss.getMetadata().getName()),
                    serviceRuntimeGateway.toSnapshot(ss)));
        }
        for (Deployment d : serviceRuntimeGateway.listDeployments(namespace)) {
            workloads.add(toEntity(namespace, d.getMetadata().getName(),
                    d.getSpec() != null ? d.getSpec().getReplicas() : null,
                    serviceByName.get(d.getMetadata().getName()),
                    serviceRuntimeGateway.toSnapshot(d)));
        }

        // 厂商顺序：按 workload 的创建时间倒序，再取首页（TenxService.java:2356）
        return workloads.stream()
                .sorted(Comparator.comparing(ServiceRuntimeEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DEFAULT_PAGE_SIZE)
                .toList();
    }

    /** 从原样快照里取创建时间（用于厂商那套"按创建时间倒序"）。 */
    private String creationTimestampOf(Map<String, Object> snapshot) {
        Object metadata = snapshot.get("metadata");
        return metadata instanceof Map<?, ?> map ? (String) map.get("creationTimestamp") : null;
    }

    private ServiceRuntimeEntity toEntity(String namespace, String name, Integer replicas,
                                          Service svc, Map<String, Object> workloadSnapshot) {
        ServicePort firstPort = null;
        if (svc != null && svc.getSpec() != null && svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty()) {
            firstPort = svc.getSpec().getPorts().get(0);
        }

        String targetPort = null;
        if (firstPort != null && firstPort.getTargetPort() != null) {
            // 具名端口要原样保留字符串形态
            targetPort = firstPort.getTargetPort().getStrVal() != null
                    ? firstPort.getTargetPort().getStrVal()
                    : String.valueOf(firstPort.getTargetPort().getIntVal());
        }

        return ServiceRuntimeEntity.builder()
                .name(name)
                .namespace(namespace)
                .type(svc != null && svc.getSpec() != null ? svc.getSpec().getType() : null)
                .portName(firstPort != null ? firstPort.getName() : null)
                .port(firstPort != null ? firstPort.getPort() : null)
                .targetPort(targetPort)
                .protocol(firstPort != null ? firstPort.getProtocol() : null)
                .replicas(replicas)
                .createdAt(creationTimestampOf(workloadSnapshot))
                .serviceSnapshot(svc != null ? serviceRuntimeGateway.toSnapshot(svc) : null)
                .deploymentSnapshot(workloadSnapshot)
                .build();
    }
}
