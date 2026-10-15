package com.csm.paas.core.domain.cicd.adapter.port;

import com.csm.paas.core.domain.cicd.model.valobj.CicdBuildJobVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobPodVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobStatusVO;

import java.util.List;

/**
 * 镜像自动构建的 K8s 入口（厂商 `CicdService` 里对 `K8sApiClientManager` 的五处调用）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/CicdService.java`
 * `getJobLog:157-182`、`cicdList:193-279`、`continuousIntegrationsV2:489-520`。
 *
 * <p><b>类 C：本环境不可验证</b>——Job 的创建/日志读取依赖集群里有 `ci-job-image` 镜像与
 * `cur-etcd` 节点标签；本平台不复现这两样，故只保证**报文与厂商逐字段一致**（离线断言），
 * 不做在线对拉。读不到 Job/Pod 时按厂商吞掉异常（返回 null / 空），**不抛**。
 */
public interface ICicdBuildPort {

    /**
     * 创建构建 Job 并返回**创建后的快照**（厂商 `batchV1Api.createNamespacedJob(K8s.NAMESPACE, v1Job, ...)`，
     * `CicdService:493`）。
     *
     * <p>厂商 Job 报文的常量部分（照抄、写死在实现里）：
     * <ul>
     *   <li>`kind=Job`、`spec.ttlSecondsAfterFinished=12h`（43200 秒）、`backoffLimit=0`、
     *       `restartPolicy=Never`、`imagePullPolicy=Always`、`command=["bash","/opt/build.sh"]`；</li>
     *   <li>三个 hostPath 卷：`docker`→`/var/run/docker.sock`、`cicd`→{@code nodeDir}、
     *       `timezone`→`/etc/localtime`（type=File）；</li>
     *   <li>`nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions`
     *       = `[{cur-etcd Exists}, {beta.kubernetes.io/arch In [amd64]}]`；</li>
     *   <li>`tolerations=[{node-role.kubernetes.io/control-plane Exists NoSchedule}]`。</li>
     * </ul>
     */
    java.util.Map<String, Object> createBuildJob(String namespace, CicdBuildJobVO job);

    /**
     * 读 Job 状态；**读不到（不存在/异常）返回 null**——厂商 `CicdService:222-255`
     * 把 `readNamespacedJob` 的 `ApiException` 整段吞掉（连"更新 job_status"一起跳过）。
     */
    CicdJobStatusVO readJobStatus(String namespace, String jobName);

    /**
     * 列**全部命名空间**的 pod 并按标签 `job-name` 建索引
     * （厂商 `CicdService.cicdList:198-212` 的 `listPodForAllNamespaces` + `labels.get("job-name")`；
     * 没有 `job-name` 标签的 pod 直接跳过）。
     *
     * <p>读失败 ⇒ **空列表**（厂商那段的 `catch (Exception) { log.error }`）。
     */
    List<CicdJobPodVO> listBuildPods();

    /**
     * 按标签 `job-name == jobName` 找 pod 并读日志
     * （厂商 `CicdService.getJobLog:157-182`）。
     *
     * <p>照抄点：厂商**先列全部命名空间的 pod**、逐个比 `labels.get("job-name")`，命中第一个就 `break`；
     * 一个都没命中时 `podName` 保持 **null**，随后照样调 `readNamespacedPodLog(null, …)` ⇒
     * 抛 `ApiException` ⇒ 被吞掉 ⇒ 返回 **null**。本方法复刻该结果（返回 null）。
     *
     * <p>`labels` 为 null 时厂商 `labels.get(...)` 会 NPE——那发生在 `for` 里且**没有 try**，
     * 所以厂商会 500。本实现同样不兜（保持 NPE 语义）。
     */
    String readJobLog(String namespace, String jobName);
}
