package com.csm.paas.core.infrastructure.gateway;

import com.csm.paas.core.domain.cicd.model.valobj.CicdBuildJobVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobPodVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobStatusVO;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自动构建的 K8s 网关（fabric8 7 复刻厂商 `CicdService` 里的
 * `BatchV1Api.createNamespacedJob` / `readNamespacedJob`、`CoreV1Api.listPodForAllNamespaces` /
 * `readNamespacedPodLog`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/CicdService.java:157-182`（日志）、
 * `:193-212`（列 pod 建 `job-name` 索引）、`:489-493`（创建 Job 报文）。
 *
 * <p><b>类 C：本环境不可验证</b>——需要有 `ci-job-image:<tag>` 镜像与带 `cur-etcd` 标签的节点；
 * 这里只保证**报文逐字段照抄**，不做在线对拉。
 *
 * <p>创建 Job 报文的常量与厂商一一对应（`CicdService:490`）：
 * `ttlSecondsAfterFinished=12h`、`backoffLimit=0`、`restartPolicy=Never`、
 * `imagePullPolicy=Always`、`command=["bash","/opt/build.sh"]`、
 * nodeAffinity `[{cur-etcd Exists}, {beta.kubernetes.io/arch In [amd64]}]`、
 * toleration `node-role.kubernetes.io/control-plane Exists NoSchedule`。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CicdBuildGateway {

    /** 厂商 `Duration.ofHours(12L).getSeconds()`（`CicdService:490`）。 */
    private static final int TTL_SECONDS_AFTER_FINISHED = (int) java.time.Duration.ofHours(12L).getSeconds();

    private final KubernetesClient kubernetesClient;

    /**
     * 厂商 `batchV1Api.createNamespacedJob(K8s.NAMESPACE, v1Job, null, null, null, null)`
     * （`CicdService:493`）。
     */
    public Map<String, Object> createBuildJob(String namespace, CicdBuildJobVO spec) {
        Job job = new JobBuilder()
                .withKind("Job")
                .withNewMetadata().withName(spec.getName()).endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(TTL_SECONDS_AFTER_FINISHED)
                .withBackoffLimit(0)
                .withNewTemplate()
                .withNewSpec()
                // volumes：docker / cicd / timezone
                .addNewVolume().withName("docker")
                .withNewHostPath().withPath("/var/run/docker.sock").withType("").endHostPath()
                .endVolume()
                .addNewVolume().withName("cicd")
                .withNewHostPath().withPath(spec.getNodeDir()).endHostPath()
                .endVolume()
                .addNewVolume().withName("timezone")
                .withNewHostPath().withPath("/etc/localtime").withType("File").endHostPath()
                .endVolume()
                .addNewContainer()
                .withName(spec.getName())
                .withImage(spec.getImage())
                .withImagePullPolicy("Always")
                .withCommand("bash", "/opt/build.sh")
                .addNewVolumeMount().withName("docker").withReadOnly(true).withMountPath("/var/run/docker.sock").endVolumeMount()
                .addNewVolumeMount().withName("cicd").withReadOnly(false).withMountPath("/cicd").endVolumeMount()
                .addNewVolumeMount().withName("timezone").withReadOnly(true).withMountPath("/etc/localtime").endVolumeMount()
                .addNewEnv().withName("filename").withValue(spec.getFormName()).endEnv()
                .addNewEnv().withName("dockerfileid").withValue("Dockerfile").endEnv()
                .addNewEnv().withName("buildfileid").withValue(spec.getBuildName()).endEnv()
                .addNewEnv().withName("username").withValue(spec.getUsername()).endEnv()
                .addNewEnv().withName("password").withValue(spec.getPassword()).endEnv()
                .addNewEnv().withName("harbor").withValue(spec.getHarbor()).endEnv()
                .addNewEnv().withName("name").withValue(spec.getFormName()).endEnv()
                .addNewEnv().withName("tag").withValue(spec.getTag()).endEnv()
                .addNewEnv().withName("storage").withValue(spec.getStorage()).endEnv()
                .addNewEnv().withName("imageInfo").withValue(spec.getImageInfo()).endEnv()
                .endContainer()
                .withAffinity(new AffinityBuilder()
                        .withNewNodeAffinity()
                        .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                        .addNewNodeSelectorTerm()
                        .addNewMatchExpression().withKey("cur-etcd").withOperator("Exists").endMatchExpression()
                        .addNewMatchExpression().withKey("beta.kubernetes.io/arch").withOperator("In")
                        .withValues("amd64").endMatchExpression()
                        .endNodeSelectorTerm()
                        .endRequiredDuringSchedulingIgnoredDuringExecution()
                        .endNodeAffinity()
                        .build())
                .addNewToleration().withKey("node-role.kubernetes.io/control-plane")
                .withOperator("Exists").withEffect("NoSchedule").endToleration()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        Job created = kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();
        return created == null ? null : toSnapshot(created);
    }

    /**
     * 读 Job 状态（厂商 `batchV1Api.readNamespacedJob`，`CicdService:223`）。
     * <p>对象不存在 ⇒ **null**（厂商此时 ApiException，整段状态更新被跳过）。
     */
    public CicdJobStatusVO readJobStatus(String namespace, String jobName) {
        Job job = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
        if (job == null || job.getStatus() == null) {
            return null;
        }
        return CicdJobStatusVO.builder()
                .active(job.getStatus().getActive())
                .succeeded(job.getStatus().getSucceeded())
                .failed(job.getStatus().getFailed())
                .ready(job.getStatus().getReady())
                .build();
    }

    /**
     * 列**全部命名空间**的 pod 并按 `job-name` 标签建索引
     * （厂商 `CicdService:198-212`）；没有 `job-name` 标签的 pod 跳过。
     */
    public List<CicdJobPodVO> listBuildPods() {
        List<CicdJobPodVO> result = new ArrayList<>();
        List<Pod> items;
        try {
            items = kubernetesClient.pods().inAnyNamespace().list().getItems();
        } catch (Exception e) {
            log.error("获取pod列表异常", e);
            return result;
        }
        for (Pod item : items) {
            if (item.getMetadata() == null) {
                continue;
            }
            Map<String, String> labels = item.getMetadata().getLabels();
            if (labels == null) {
                continue;
            }
            String jobName = labels.get("job-name");
            if (jobName == null) {
                continue;
            }
            CicdJobPodVO vo = new CicdJobPodVO();
            vo.setJobName(jobName);
            vo.setPodName(item.getMetadata().getName());
            vo.setNamespace(item.getMetadata().getNamespace());
            // 厂商这段的容器名读取包在 try/catch 里（spec 为 null ⇒ 只记日志，pod 仍带 name/namespace）
            try {
                if (item.getSpec() != null && item.getSpec().getContainers() != null) {
                    vo.setContainer(item.getSpec().getContainers().stream()
                            .map(io.fabric8.kubernetes.api.model.Container::getName).toList());
                }
            } catch (Exception e) {
                log.error("获取pod信息异常", e);
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 读构建 Job 的 pod 日志（厂商 `CicdService:157-182`）。
     *
     * <p>照抄点：厂商按 `labels.get("job-name")` 找**第一个**命中的 pod；
     * 找不到时 `podName` 为 null，随后照样调 `readNamespacedPodLog(null, …)` ⇒ ApiException ⇒ 吞掉 ⇒ 返回 null。
     * <p>厂商对 `labels` 为 null **不做判断**（会 NPE ⇒ 500），本实现对同一处保持 NPE。
     * <p>日志读取用的是 **pod 所属命名空间**：厂商写死 `K8s.NAMESPACE`。本方法按厂商签名收 namespace。
     */
    public String readJobLog(String namespace, String jobName) {
        List<Pod> items;
        try {
            items = kubernetesClient.pods().inAnyNamespace().list().getItems();
        } catch (Exception e) {
            log.error("获取pod列表失败", e);
            return null;
        }
        String podName = null;
        for (Pod item : items) {
            Map<String, String> labels = item.getMetadata().getLabels();
            String name = labels.get("job-name");
            if (name == null || !name.equals(jobName)) {
                continue;
            }
            podName = item.getMetadata().getName();
            break;
        }
        if (podName == null) {
            // 厂商把 null 传给 readNamespacedPodLog ⇒ ApiException ⇒ 吞掉 ⇒ null
            log.error("获取日志失败: 未找到 job-name={} 的 pod", jobName);
            return null;
        }
        try {
            return kubernetesClient.pods().inNamespace(namespace).withName(podName).getLog();
        } catch (Exception e) {
            log.error("获取日志失败", e);
            return null;
        }
    }

    /** 与 `ServiceRuntimeGateway.toSnapshot` 同款：fabric8 模型 → `Map` 快照。 */
    private static Map<String, Object> toSnapshot(Object k8sObject) {
        try {
            return Serialization.jsonMapper().readValue(Serialization.asJson(k8sObject), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("K8s 对象快照序列化失败", e);
        }
    }
}
