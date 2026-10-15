package com.csm.paas.core.domain.cluster.adapter.port;

import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;

import java.util.List;
import java.util.Map;

/**
 * 公共 API（`/v1/pub`）所需的集群读写入口 —— 现有 {@code IServiceRuntimePort} 之外的补充。
 *
 * <p>为什么另起一个端口：本次任务的文件白名单只放行 `domain/cluster/**`，不放行
 * `domain/software/**`（`IServiceRuntimePort` 所在包），所以新增的 K8s 读/写都落在这里。
 *
 * <p>厂商出处（fabric8 7 复刻 io.kubernetes client-java）：
 * <ul>
 *   <li>`PublicController.allServiceStatus:442-459` 的 `tenxCloud.allPageService(null)`；</li>
 *   <li>`PublicController.getServiceStatusInfo:2428-2585` 的
 *       `K8sApiClientManager.getPodList(null)`、`appsV1Api.listNamespacedDeployment`、
 *       `readNamespacedDeployment(gis)`、`readNamespacedStatefulSet`；</li>
 *   <li>`FileService.patchServiceResource:1138-1233` 的读 Deployment / StatefulSet 与 merge-patch。</li>
 * </ul>
 *
 * <p>快照统一用 `Map&lt;String,Object&gt;`（与 `IServiceRuntimePort`/`ServiceRuntimeGateway.toSnapshot`
 * 一致的口径：fabric8 模型 `Serialization.asJson` 后的 JSON 结构）。
 */
public interface IPubClusterPort {

    /**
     * 列某命名空间**全部 Deployment** 的原始快照
     * （厂商 `appsV1Api.listNamespacedDeployment(K8s.NAMESPACE, …)`）。
     *
     * <p>厂商 `getServiceStatusInfo:2472-2479` 对 `ApiException` 是**吞掉并返回已构造的部分响应**
     * （只有 sysInfo 的那种）；`allServiceStatus:444` 走 tenxCloud，本平台改用同集群 Deployment 直读
     * （与 `ModeServiceImpl.getListWithOutGaop` 的既有替代口径一致）。
     * <p>读失败抛 {@code K8sApiException}：领域层据此复刻厂商"提前返回半成品响应"的分支
     * （把失败与"真的一个 Deployment 都没有"区分开）。
     */
    List<Map<String, Object>> listDeploymentSnapshots(String namespace);

    /**
     * 列**全部命名空间**的 pod 快照（厂商 `K8sApiClientManager.getPodList(null)` ⇒
     * `coreV1Api.listPodForAllNamespaces`）。
     *
     * <p>照抄点：厂商这个方法**读失败会抛** `SystemException("k8s.call.api.failed")`（500），
     * 不像其它读那样吞异常（`K8sApiClientManager.java:111-114`）。本端口同样抛 `K8sApiException`。
     */
    List<Map<String, Object>> listAllPodSnapshots();

    /**
     * 按名读 Deployment 快照；**不存在返回 null**（厂商 `readNamespacedDeployment` 抛 ApiException）。
     * <p>用于 `patchServiceResource` 的"读不到 ⇒ 回退 StatefulSet"分支。
     */
    Map<String, Object> readDeploymentSnapshot(String namespace, String name);

    /** 按名读 StatefulSet 快照；**不存在返回 null**（厂商 `readNamespacedStatefulSet` 抛 ApiException ⇒ 400）。 */
    Map<String, Object> readStatefulSetSnapshot(String namespace, String name);

    /**
     * 给 Deployment **第一个容器**换掉 resources、并把滚动策略设成
     * `type=RollingUpdate, maxSurge=0, maxUnavailable=1`，再整份 merge-patch
     * （厂商 `FileService.patchServiceResource:1148-1182`）。
     *
     * @return **false = Deployment 读不到**（厂商此时回退 StatefulSet）；
     *         patch 失败抛 {@code K8sApiException}（厂商 ⇒ 400 `k8s.call.api.failed.to.change.deploy`）
     */
    boolean patchDeploymentResources(String namespace, PubResourceSpecVO spec);

    /**
     * 同上，但打在 StatefulSet 上，且策略只写
     * `updateStrategy.type=RollingUpdate` + `rollingUpdate.maxUnavailable=1`。
     *
     * <p><b>照抄点</b>：厂商在这个分支里也构造了 `maxSurge = 0`，但**从未使用**（死代码，
     * `FileService:1218`）——所以 StatefulSet 的 `maxSurge` 不会被写。
     *
     * @return **false = StatefulSet 读不到**（厂商 ⇒ 400 `service.get.service.info.update.resource.failed`）；
     *         patch 失败抛 {@code K8sApiException}
     */
    boolean patchStatefulSetResources(String namespace, PubResourceSpecVO spec);
}
