package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port 实现：只调 gateway，把 K8s 对象翻成领域实体（防腐层）。 */
@Slf4j
@Component
public class ServiceRuntimePort implements IServiceRuntimePort {

    @Resource
    private ServiceRuntimeGateway serviceRuntimeGateway;

    @Override
    public List<ServiceRuntimeEntity> queryServices(String namespace) {
        Map<String, Deployment> deployments = new LinkedHashMap<>();
        for (Deployment d : serviceRuntimeGateway.listDeployments(namespace)) {
            deployments.put(d.getMetadata().getName(), d);
        }

        List<ServiceRuntimeEntity> result = new ArrayList<>();
        for (Service svc : serviceRuntimeGateway.listServices(namespace)) {
            String name = svc.getMetadata().getName();
            Deployment deployment = deployments.get(name);
            result.add(toEntity(svc, deployment, namespace));
        }
        return result;
    }

    private ServiceRuntimeEntity toEntity(Service svc, Deployment deployment, String namespace) {
        ServicePort firstPort = (svc.getSpec() != null && svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty())
                ? svc.getSpec().getPorts().get(0) : null;

        List<Integer> nodePorts = new ArrayList<>();
        if (svc.getSpec() != null && svc.getSpec().getPorts() != null) {
            for (ServicePort p : svc.getSpec().getPorts()) {
                if (p.getNodePort() != null) {
                    nodePorts.add(p.getNodePort());
                }
            }
        }

        Integer readyReplicas = null;
        Integer replicas = null;
        if (deployment != null && deployment.getStatus() != null) {
            readyReplicas = deployment.getStatus().getReadyReplicas();
            replicas = deployment.getStatus().getReplicas();
        }

        // 具名端口要原样保留字符串形态
        String targetPort = null;
        if (firstPort != null && firstPort.getTargetPort() != null) {
            targetPort = firstPort.getTargetPort().getStrVal() != null
                    ? firstPort.getTargetPort().getStrVal()
                    : String.valueOf(firstPort.getTargetPort().getIntVal());
        }

        return ServiceRuntimeEntity.builder()
                .name(svc.getMetadata().getName())
                .namespace(namespace)
                .type(svc.getSpec() != null ? svc.getSpec().getType() : null)
                .portName(firstPort != null ? firstPort.getName() : null)
                .port(firstPort != null ? firstPort.getPort() : null)
                .targetPort(targetPort)
                .protocol(firstPort != null ? firstPort.getProtocol() : null)
                .replicas(replicas)
                .readyReplicas(readyReplicas)
                .restartCount(null)
                .hostIP(deployment != null && deployment.getSpec() != null ? null : null)
                .nodePorts(nodePorts)
                .createdAt(svc.getMetadata().getCreationTimestamp())
                .serviceSnapshot(serviceRuntimeGateway.toSnapshot(svc))
                .deploymentSnapshot(deployment != null ? serviceRuntimeGateway.toSnapshot(deployment) : null)
                .build();
    }
}
