package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.CicdDockerFileDTO;
import com.csm.paas.core.api.dto.CicdInfoDTO;
import com.csm.paas.core.api.dto.CicdListFormDTO;
import com.csm.paas.core.api.dto.CicdTaskDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;
import com.csm.paas.core.domain.cicd.model.valobj.CicdTaskItemVO;
import com.csm.paas.core.domain.cicd.service.ICicdService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 公共 API - 自动构建（厂商 `controller/pub/CicdController.java:30-88`）。
 *
 * <p>厂商的类级 RequestMapping 是 /v1/pub，本类照抄；**不实现**厂商那条
 * 源码上标注了 Deprecated 的 `POST /cicd`（fastdfs 老路径，不在本批清单里）。
 *
 * <p>厂商出处（逐条）：
 * <ul>
 *   <li>`POST /task` → `CicdService.createTask:281-305`；</li>
 *   <li>`POST /cicd_v2`（`consumes=multipart/form-data`）→ `CicdService.continuousIntegrationsV2:428-521`，
 *       控制器还额外做了一次 `job.getMetadata().setManagedFields(null)`（`CicdController:57`）；</li>
 *   <li>`POST /cicdList` → `CicdService.cicdList:184-279`；</li>
 *   <li>`GET /cicdlog?jobName=` → `CicdService.getJobLog:157-182`；</li>
 *   <li>`DELETE /delete?id=` → `CicdService.deleteTask:125-135`；</li>
 *   <li>`POST /updateTask` → `CicdService.updateDockerFile:137-155`。</li>
 * </ul>
 *
 * <p><b>`POST /cicd_v2` 的响应体</b>：厂商返回的是 kubernetes-client 的 `V1Job`（Jackson 序列化），
 * 本仓返回命令式 Map 快照——**键名与 fabric8 的 Job JSON 一致**（camelCase、`apiVersion/kind/metadata/spec/status`）。
 * 一个**主动偏离**：厂商对"Job 创建失败"返回 `new V1Job()`（metadata 为 null）后立刻 `getMetadata().setManagedFields(null)`
 * ⇒ NPE ⇒ 500；本实现对 null metadata 做保护（返回 `{}`），其余照抄。
 *
 * <p><b>类 C：本环境不可验证</b>——`/cicd_v2` 依赖 `ci-job-image` 镜像与 `cur-etcd` 节点标签，
 * `/cicdlog` 依赖真实 Job 的 pod；两者都只保证报文照抄 + 离线段言。
 */
@Slf4j
@RestController
@RequestMapping("/v1/pub")
public class CicdController {

    @Resource
    private ICicdService cicdService;

    /**
     * 厂商 `CicdController.createTask:38-43`（`POST /task`）。
     * <p>厂商该方法的 Operation 摘要是「创建任务(第一步)」。
     */
    @PostMapping("/task")
    public ResponseDTO<CicdInfoDTO> createTask(@RequestBody CicdTaskDTO form) {
        CicdEntity task = cicdService.createTask(form.getUserId(), form.getImageStorage(),
                form.getImageName(), form.getDesc(), form.getDockerfile());
        return ResponseDTO.ok(toInfo(task));
    }

    /**
     * 厂商 `CicdController.continuousIntegrationV2:53-59`（`POST /cicd_v2`，multipart）。
     *
     * <p>照抄点：控制器在返回前把 `metadata.managedFields` 置空（`CicdController:57`）——
     * 厂商用的是 client-java 的 `V1Job`，本仓对**同一路径**做 Map 上的 `remove`。
     */
    @PostMapping(value = "/cicd_v2", consumes = "multipart/form-data")
    public ResponseDTO<Map<String, Object>> continuousIntegrationV2(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        // 照抄：file 为 null 时厂商在此 NPE（500），本实现同样不兜
        String buildName = file.getOriginalFilename();
        byte[] bytes = file.getBytes();
        Map<String, Object> job = cicdService.continuousIntegrationsV2(userId, name, tag, buildName, bytes);
        if (job != null && job.get("metadata") instanceof Map<?, ?> metadata) {
            ((Map<String, Object>) metadata).remove("managedFields");
        }
        return ResponseDTO.ok(job);
    }

    /**
     * 厂商 `CicdController.cicdList:61-66`（`POST /cicdList`）。
     * <p>照抄点：响应行里的 `jobStatus` 是**入库前的旧值**（见 `ICicdService.list`）。
     */
    @PostMapping("/cicdList")
    public ResponseDTO<CicdInfoDTO.CicdInfoListDTO> cicdList(@RequestBody CicdListFormDTO form) {
        ICicdService.CicdPage page = cicdService.list(form.getUserId(), form.getPage(), form.getSize(),
                form.getKeyword());
        List<CicdInfoDTO.CicdListRowDTO> list = page.getList().stream().map(CicdController::toRow).toList();
        return ResponseDTO.ok(CicdInfoDTO.CicdInfoListDTO.of(list, page.getTotal()));
    }

    /**
     * 厂商 `CicdController.getJobLog:68-73`（`GET /cicdlog?jobName=`）。
     * <p>注意厂商该参数**没有 required=false**，即 jobName 必填；
     * 缺失时 Spring 抛 `MissingServletRequestParameterException`。
     */
    @GetMapping("/cicdlog")
    public ResponseDTO<String> getJobLog(@RequestParam String jobName) {
        return ResponseDTO.ok(cicdService.getJobLog(jobName));
    }

    /** 厂商 `CicdController.deleteTask:75-80`（`DELETE /delete?id=`）。 */
    @DeleteMapping("/delete")
    public ResponseDTO<Boolean> deleteTask(@RequestParam Integer id) {
        return ResponseDTO.ok(cicdService.deleteTask(id));
    }

    /**
     * 厂商 `CicdController.updateDockerFile:82-87`（`POST /updateTask`）。
     * <p>照抄点：`dockerfile` 为 null 时返回 `false` 且**什么也不落库**（连 desc 一起丢）。
     */
    @PostMapping("/updateTask")
    public ResponseDTO<Boolean> updateDockerFile(@RequestBody CicdDockerFileDTO form) {
        return ResponseDTO.ok(cicdService.updateDockerFile(form.getId(), form.getDesc(), form.getDockerfile()));
    }

    // ------------------------------------------------------------------ 组装

    /** 厂商 `BeanUtils.copyProperties(c, cicdWithPodInfo)` 的等价映射（含 pod；pod 可为 null）。 */
    private static CicdInfoDTO.CicdListRowDTO toRow(CicdTaskItemVO item) {
        CicdInfoDTO.CicdPodDTO pod = null;
        if (item.getPod() != null) {
            pod = CicdInfoDTO.CicdPodDTO.builder()
                    .podName(item.getPod().getPodName())
                    .namespace(item.getPod().getNamespace())
                    .container(new java.util.ArrayList<>(item.getPod().getContainer()))
                    .build();
        }
        return CicdInfoDTO.CicdListRowDTO.builder()
                .cicd(toInfo(item.getCicd()))
                .pod(pod)
                .build();
    }

    private static CicdInfoDTO toInfo(CicdEntity c) {
        return CicdInfoDTO.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .imageName(c.getImageName())
                .imageTag(c.getImageTag())
                .dockerfileId(c.getDockerfileId())
                .dockerfileContent(c.getDockerfileContent())
                .buildfileId(c.getBuildfileId())
                .jobName(c.getJobName())
                .jobStatus(c.getJobStatus())
                .description(c.getDescription())
                .createAt(c.getCreateAt())
                .updateAt(c.getUpdateAt())
                .imageStorage(c.getImageStorage())
                .build();
    }
}
