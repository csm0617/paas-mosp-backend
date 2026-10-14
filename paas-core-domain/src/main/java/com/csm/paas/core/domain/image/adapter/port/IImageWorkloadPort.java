package com.csm.paas.core.domain.image.adapter.port;

import com.csm.paas.core.domain.image.model.valobj.ImageWorkloadVO;

import java.util.List;

/**
 * 影像域读 Kubernetes Deployment 的端口
 * （厂商 `K8sApiClientManager.getV1Deployments(K8s.NAMESPACE, …)`，
 * 命名空间 = `tenx.open.api.team-space`，paas-core 里是 `gaop.k8s.namespace`）。
 */
public interface IImageWorkloadPort {

    /** 列出命名空间下全部 Deployment（裁剪字段见 {@link ImageWorkloadVO}）。 */
    List<ImageWorkloadVO> listDeployments();
}
