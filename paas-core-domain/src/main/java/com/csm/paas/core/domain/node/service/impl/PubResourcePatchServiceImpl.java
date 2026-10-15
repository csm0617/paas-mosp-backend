package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.cluster.adapter.port.IPubClusterPort;
import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;
import com.csm.paas.core.domain.node.service.IPubResourcePatchService;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * `POST /v1/pub/patchResource`（厂商 `FileService.patchServiceResource:1138-1233`）。
 *
 * <p>照抄点：Deployment 与 StatefulSet 两个分支的**读取失败**语义不同
 * （前者回退、后者直接 400），而**patch 失败**语义相同（都是 400 `k8s.call.api.failed.to.change.deploy`）。
 *
 * <p><b>在线验证说明</b>：这条会真的改工作负载的 resources 与滚动策略。本项目纪律是不拿集群里的
 * 真实工作负载做实验，所以只做**离线断言**（对 `IPubClusterPort` 的桩），不做在线对拉。
 */
@Slf4j
@Service
public class PubResourcePatchServiceImpl implements IPubResourcePatchService {

    /** 厂商 `K8s.NAMESPACE`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Resource
    private IPubClusterPort pubClusterPort;

    @Override
    public boolean patchServiceResource(PubResourceSpecVO spec) {
        boolean deployment;
        try {
            // 读不到 Deployment ⇒ false（厂商 `readNamespacedDeployment` 抛 ApiException ⇒ 回退）
            deployment = pubClusterPort.patchDeploymentResources(namespace, spec);
        } catch (K8sApiException e) {
            // patch 失败 ⇒ 400（厂商 `FileService:1179-1182`）
            log.error("调用API失败: {}", e.getMessage());
            throw new WrongArgumentException("k8s.call.api.failed.to.change.deploy");
        }
        if (deployment) {
            return true;
        }
        return patchStatefulSet(spec);
    }

    /** 厂商 `FileService.patchServiceResourceForStatefulSet:1185-1233`。 */
    private boolean patchStatefulSet(PubResourceSpecVO spec) {
        boolean statefulSet;
        try {
            statefulSet = pubClusterPort.patchStatefulSetResources(namespace, spec);
        } catch (K8sApiException e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new WrongArgumentException("k8s.call.api.failed.to.change.deploy");
        }
        if (!statefulSet) {
            // 读不到 StatefulSet ⇒ 400（厂商 `FileService:1191-1194`）
            throw new WrongArgumentException("service.get.service.info.update.resource.failed");
        }
        return true;
    }
}
