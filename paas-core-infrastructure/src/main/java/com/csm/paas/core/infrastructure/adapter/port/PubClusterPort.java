package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.cluster.adapter.port.IPubClusterPort;
import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;
import com.csm.paas.core.infrastructure.gateway.PubClusterGateway;
import com.csm.paas.core.types.exception.K8sApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Port 实现：把 fabric8 的失败翻译成 domain 可见的 `K8sApiException`（只调 gateway）。
 *
 * <p><b>类 C：本环境不可验证</b>——读的部分可以在真集群上跑，写的部分（resources patch）
 * 按项目纪律不做在线实验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubClusterPort implements IPubClusterPort {

    private final PubClusterGateway pubClusterGateway;

    @Override
    public List<Map<String, Object>> listDeploymentSnapshots(String namespace) {
        try {
            return pubClusterGateway.listDeploymentSnapshots(namespace);
        } catch (Exception e) {
            log.error("获取所有的deployment出现异常，异常为:{}", e.getMessage());
            throw new K8sApiException("获取Deployment列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listAllPodSnapshots() {
        try {
            return pubClusterGateway.listAllPodSnapshots();
        } catch (Exception e) {
            // 厂商 `K8sApiClientManager.getPodList` 读失败 ⇒ SystemException("k8s.call.api.failed")（500）
            log.error("调用k8s pod list 异常", e);
            throw new K8sApiException("k8s.call.api.failed");
        }
    }

    @Override
    public Map<String, Object> readDeploymentSnapshot(String namespace, String name) {
        try {
            return pubClusterGateway.readDeploymentSnapshot(namespace, name);
        } catch (Exception e) {
            log.error("读取Deployment {}失败: {}", name, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> readStatefulSetSnapshot(String namespace, String name) {
        try {
            return pubClusterGateway.readStatefulSetSnapshot(namespace, name);
        } catch (Exception e) {
            log.error("读取StatefulSet {}失败: {}", name, e.getMessage());
            throw new K8sApiException("读取StatefulSet失败: " + e.getMessage());
        }
    }

    @Override
    public boolean patchDeploymentResources(String namespace, PubResourceSpecVO spec) {
        try {
            return pubClusterGateway.patchDeploymentResources(namespace, spec);
        } catch (Exception e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new K8sApiException("patch Deployment resources 失败: " + e.getMessage());
        }
    }

    @Override
    public boolean patchStatefulSetResources(String namespace, PubResourceSpecVO spec) {
        try {
            return pubClusterGateway.patchStatefulSetResources(namespace, spec);
        } catch (Exception e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new K8sApiException("patch StatefulSet resources 失败: " + e.getMessage());
        }
    }
}
