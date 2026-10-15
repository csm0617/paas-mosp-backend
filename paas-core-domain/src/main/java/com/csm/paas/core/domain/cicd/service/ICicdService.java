package com.csm.paas.core.domain.cicd.service;

import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;

import java.io.IOException;
import java.util.List;

/**
 * 公共 API - 自动构建（厂商 `controller/pub/CicdController.java` + `service/CicdService.java`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/pub/CicdController.java:38-87` 的 6 条端点，
 * 实现全部在 `service/CicdService.java`。
 *
 * <p>本接口**只覆盖本次要做的那 6 条**：
 * `POST /task`、`POST /cicd_v2`、`POST /cicdList`、`GET /cicdlog`、`POST /updateTask`、`DELETE /delete`。
 * 厂商的 `POST /cicd`（源码上标注了 Deprecated 的 fastdfs 老路径）**不在本批**，故不在接口里。
 */
public interface ICicdService {

    /** 厂商 `CicdService.createTask:281-305`。 */
    CicdEntity createTask(String userId, String imageStorage, String imageName, String desc, String dockerfile);

    /** 厂商 `CicdService.deleteTask:125-135`。 */
    boolean deleteTask(Integer id);

    /** 厂商 `CicdService.updateDockerFile:137-155`。 */
    boolean updateDockerFile(Integer id, String desc, String dockerfile);

    /** 厂商 `CicdService.getJobLog:157-182`。 */
    String getJobLog(String jobName);

    /**
     * 厂商 `CicdService.cicdList:184-279` 的**查询结果**。
     *
     * <p>返回 {@code [list, total]}：`list` 是已回填 `pod` 与（落库后的）`jobStatus` 的行。
     * <p><b>照抄点（重要）</b>：厂商先 `BeanUtils.copyProperties(c, cicdWithPodInfo)` **再**在
     * `c` 上算新的 `jobStatus`（`:226-251`）——所以**响应里的 `jobStatus` 是入库前的旧值**，
     * 而库里已经是新值。本方法照抄：返回的 `CicdEntity` 携带**旧** `jobStatus`。
     */
    CicdPage list(String userId, Integer page, Integer size, String keyword);

    /**
     * 厂商 `CicdService.continuousIntegrationsV2:428-521`。
     * <p>返回创建后的 Job 快照（`Map`），交由 trigger 层剥掉 `metadata.managedFields`
     * （厂商 `CicdController:57`）。
     *
     * @param name    构建任务名（对应 `g_ci_cd.image_name`）
     * @param userId  当前用户
     * @param tag     可选 tag（空白 ⇒ 用 `yyyyMMdd-6位随机`）
     * @param buildName 上传的构建文件原始名（`form.getFile().getOriginalFilename()`）
     * @param fileBytes 构建文件内容（trigger 层已读成字节，避免 domain 依赖 MultipartFile）
     */
    java.util.Map<String, Object> continuousIntegrationsV2(String userId, String name, String tag,
                                                          String buildName, byte[] fileBytes) throws IOException;

    /** 厂商 `CicdService.cicdList` 的返回体（`controller/response/cicd/CiCdInfoList`）。 */
    @lombok.Data
    @lombok.AllArgsConstructor
    class CicdPage {
        private final List<com.csm.paas.core.domain.cicd.model.valobj.CicdTaskItemVO> list;
        private final long total;
    }
}
