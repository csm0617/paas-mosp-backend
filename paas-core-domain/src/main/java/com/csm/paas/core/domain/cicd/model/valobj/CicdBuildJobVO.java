package com.csm.paas.core.domain.cicd.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建自动构建 Job 所需的全部参数（厂商 `CicdService.continuousIntegrationsV2:489-490` 那一个巨大的
 * `V1Job.builder` 链式表达式里**除 K8s 模型以外**的输入）。
 *
 * <p>把 fabric8/kubernetes-client 挡在 domain 外：domain 只给值，Job 报文由
 * {@code infrastructure/gateway/CicdBuildGateway} 拼装（`application/…` 无 K8s 依赖）。
 *
 * <p>字段与厂商那个链的对应关系（逐项照抄）：
 * <table>
 *   <tr><td>`name`</td><td>Job 名 + 容器名（`form.getName() + "-" + 6 位随机串`）</td></tr>
 *   <tr><td>`image`</td><td>`harbor + "/system_containers/ci-job-image:" + cicd.image_version`</td></tr>
 *   <tr><td>`nodeDir`</td><td>`mount.path + "/gaop-data/upload/cicd/" + cicd.id`（hostPath 卷 `cicd`）</td></tr>
 *   <tr><td>`buildName`</td><td>`form.getFile().getOriginalFilename()`（env `buildfileid`）</td></tr>
 *   <tr><td>`formName`</td><td>`form.getName()`（env `filename` / `name`）</td></tr>
 *   <tr><td>`tag` / `storage`</td><td>env `tag` / `storage`</td></tr>
 *   <tr><td>`harbor`</td><td>去协议头的 harbor 地址（env `harbor`）</td></tr>
 *   <tr><td>`username` / `password`</td><td>`docker.username` / `docker.password`</td></tr>
 *   <tr><td>`imageInfo`</td><td>`imageupload`</td></tr>
 * </table>
 * 常量部分（`dockerfileid` = `"Dockerfile"`、`volumeMounts`、`affinity`、`tolerations`、
 * `restartPolicy`、`backoffLimit`、`ttlSecondsAfterFinished`）写死在 gateway 里，因为它们**不可配**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdBuildJobVO {

    private String name;
    private String image;
    private String nodeDir;
    private String buildName;
    private String formName;
    private String tag;
    private String storage;
    private String harbor;
    private String username;
    private String password;
    private String imageInfo;
}
