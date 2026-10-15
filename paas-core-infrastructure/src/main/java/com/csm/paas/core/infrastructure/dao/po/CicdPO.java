package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_ci_cd` 的投影（厂商 `com.glriverside.gaop.be.model.Cicd`）。
 * <p>表名与列名出处：厂商 `dao/CicdDynamicSqlSupport.java:34-43`（`super("g_ci_cd")`，
 * 列 `id/user_id/image_name/image_tag/dockerfile_id/dockerfile_content/buildfile_id/job_name/job_status/description/create_at/update_at/image_storage`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdPO {

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
}
