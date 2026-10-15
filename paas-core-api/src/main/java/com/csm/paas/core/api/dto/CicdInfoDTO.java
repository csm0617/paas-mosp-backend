package com.csm.paas.core.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建任务的 13 个字段（厂商 `com.glriverside.gaop.be.model.Cicd` 的字段与声明顺序）。
 *
 * <p>用途：`POST /v1/pub/task` 的响应体就是**这一个**对象——厂商返回 `Response<Cicd>`，
 * **没有 `pod` 键**（`CicdController:40-43`）。`/cicdList` 的行用 {@link CicdListRowDTO}。
 *
 * <p>字段顺序照抄 `model/Cicd.java:6-18`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdInfoDTO {

    private Integer id;
    private String userId;
    private String imageName;
    private String imageTag;
    private String dockerfileId;
    private String dockerfileContent;
    private String buildfileId;
    private String jobName;
    private Integer jobStatus;
    private String description;
    private Long createAt;
    private Long updateAt;
    private String imageStorage;

    /**
     * `/cicdList` 的一行（厂商 `controller/response/cicd/CicdWithPodInfo extends Cicd`）。
     *
     * <p>厂商是**继承** ⇒ JSON 是扁平的"13 个字段 + `pod`"；这里用 Jackson 的 JsonUnwrapped 把
     * {@link CicdInfoDTO} 摊平在同一层，保证键名与顺序完全一致（`pod` 在最后，且 **null 也会出现**：
     * 厂商只在 `jobName != null` 时 `setPod`，jobName 为空的历史行就是 `"pod":null`）。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CicdListRowDTO {

        @JsonUnwrapped
        private CicdInfoDTO cicd;

        private CicdPodDTO pod;
    }

    /** `POST /v1/pub/cicdList` 的响应体（厂商 `controller/response/cicd/CiCdInfoList.java:10-14`）。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CicdInfoListDTO {

        private List<CicdListRowDTO> list;

        /** 厂商 `CiCdInfoList.total` 是 **long**。 */
        private long total;

        public static CicdInfoListDTO of(List<CicdListRowDTO> list, long total) {
            return CicdInfoListDTO.builder().list(list).total(total).build();
        }
    }

    /** 厂商 `controller/response/cicd/PodContainerInfo.java:10-16`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CicdPodDTO {

        private String podName;
        private String namespace;

        /** 厂商声明即初始化成空列表 ⇒ 空对象序列化成 `{"container":[]}`。 */
        @Builder.Default
        private List<String> container = new ArrayList<>();
    }
}
