package com.csm.paas.core.domain.cicd.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建 Job 的 Pod 信息（厂商 `CicdService.cicdList:256-271` 填的
 * `controller/response/cicd/PodContainerInfo`：`podName` / `namespace` / `container`）。
 *
 * <p>vendor 的 `container` 字段**声明时就初始化成空列表**（`PodContainerInfo.java:16`
 * `= new ArrayList&lt;String&gt;()`），所以取不到 pod 时返回的是
 * `{"pod":{}}`（不是 `{"pod":null}`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdJobPodVO {

    private String jobName;
    private String podName;
    private String namespace;

    @Builder.Default
    private List<String> container = new ArrayList<>();
}
