package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.image.adapter.port.IImageWorkloadPort;
import com.csm.paas.core.domain.image.model.valobj.ImageWorkloadVO;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 影像域读 Deployment（厂商 `K8sApiClientManager.getV1Deployments(K8s.NAMESPACE, …)`）。
 *
 * <p>命名空间 = `tenx.open.api.team-space`（厂商 `K8s.java:16`，默认 `gaop-namespace`），
 * paas-core 里用 `gaop.k8s.namespace`（既有约定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageWorkloadPort implements IImageWorkloadPort {

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    private final ServiceRuntimeGateway serviceRuntimeGateway;

    @Override
    public List<ImageWorkloadVO> listDeployments() {
        List<Deployment> deployments = serviceRuntimeGateway.listDeployments(namespace);
        List<ImageWorkloadVO> result = new ArrayList<>(deployments.size());
        for (Deployment deployment : deployments) {
            result.add(toVO(deployment));
        }
        return result;
    }

    private ImageWorkloadVO toVO(Deployment deployment) {
        ObjectMeta meta = deployment.getMetadata();
        List<String> containerImages = new ArrayList<>();
        if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getSpec() != null
                && deployment.getSpec().getTemplate().getSpec().getContainers() != null) {
            for (Container container : deployment.getSpec().getTemplate().getSpec().getContainers()) {
                containerImages.add(container.getImage());
            }
        }
        return ImageWorkloadVO.builder()
                .name(meta == null ? null : meta.getName())
                .namespace(meta == null ? null : meta.getNamespace())
                .containerImages(containerImages)
                .labels(meta == null ? null : meta.getLabels())
                .annotations(meta == null ? null : meta.getAnnotations())
                .templateAnnotations(templateAnnotations(deployment))
                .creationTimestamp(meta == null ? null : toEpochMilli(meta.getCreationTimestamp()))
                .build();
    }

    private static java.util.Map<String, String> templateAnnotations(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getMetadata() == null) {
            return null;
        }
        return deployment.getSpec().getTemplate().getMetadata().getAnnotations();
    }

    /**
     * 厂商：`Date.from(e.getMetadata().getCreationTimestamp().toLocalDateTime()
     * .atZone(ZoneId.systemDefault()).toInstant())`——**先丢掉偏移量**再按系统时区解释，照抄。
     */
    private static Long toEpochMilli(String creationTimestamp) {
        if (creationTimestamp == null || creationTimestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(creationTimestamp, DateTimeFormatter.ISO_DATE_TIME)
                    .toLocalDateTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            log.warn("解析 Deployment creationTimestamp 失败：{}", creationTimestamp);
            return null;
        }
    }
}
