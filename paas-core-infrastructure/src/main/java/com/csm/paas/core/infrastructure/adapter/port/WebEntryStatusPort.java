package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.infrastructure.gateway.ServiceRuntimeGateway;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读 K8s Deployment 的 status 算 web 入口状态（厂商 `SoftwareService.setServiceStatusBySoftware`）。
 * <p>列表拿不到时（离线/无权限）返回尽量少的信息——调用方会把"没找到"当 2 处理，与厂商"找不到同名 Deployment → 2"一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebEntryStatusPort implements IWebEntryStatusPort {

    private final ServiceRuntimeGateway serviceRuntimeGateway;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Override
    public Map<String, Integer> queryWebStatus(List<String> serviceNames) {
        Map<String, Integer> result = new HashMap<>();
        if (serviceNames == null || serviceNames.isEmpty()) {
            return result;
        }
        List<Deployment> deployments;
        try {
            deployments = serviceRuntimeGateway.listDeployments(namespace);
        } catch (Exception e) {
            log.warn("读取 Deployment 失败，web 状态按'找不到'处理: {}", e.getMessage());
            return result;
        }
        for (Deployment d : deployments) {
            String name = d.getMetadata() == null ? null : d.getMetadata().getName();
            if (name == null || !serviceNames.contains(name)) {
                continue;
            }
            var status = d.getStatus();
            if (status == null) {
                // 厂商在这里会 NPE（500）；我们按"状态缺失"推进，让上层给出 2
                continue;
            }
            result.put(name, toWeb(status.getAvailableReplicas(), status.getReadyReplicas(),
                    status.getReplicas(), status.getUpdatedReplicas(), status.getUnavailableReplicas()));
        }
        return result;
    }

    /**
     * **逐行照抄厂商** `SoftwareService.java:564-585`（含那处可疑：内层先设 3 又被无条件设 1 覆盖）。
     * <p>厂商在 `status == null` 时会 NPE；这里不可达（上面已跳过）。
     */
    static int toWeb(Integer available, Integer ready, Integer replicas, Integer updated, Integer unavailable) {
        if (available != null) {
            if (ready != null && replicas != null && updated != null) {
                return 1;
            }
            if (ready != null && replicas != null && updated != null && unavailable != null) {
                return 1;
            }
            if (replicas != null && updated != null && unavailable != null) {
                return 3;
            }
            return 2;
        }
        if (ready != null && replicas != null && updated != null && unavailable != null) {
            return 1;
        }
        if (replicas != null && updated != null && unavailable != null) {
            return 3;
        }
        return 2;
    }
}
