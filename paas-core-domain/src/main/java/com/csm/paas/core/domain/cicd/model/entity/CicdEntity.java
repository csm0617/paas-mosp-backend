package com.csm.paas.core.domain.cicd.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动构建任务（厂商 `com.glriverside.gaop.be.model.Cicd`，表 **`g_ci_cd`**）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/Cicd.java:6-18` 的 13 个字段；
 * 表名见 `dao/CicdDynamicSqlSupport.java:34`（`super("g_ci_cd")`），列名见同文件 `:36-43`。
 *
 * <p>照抄点：厂商每个 setter 都对字符串做 `trim()`（`Cicd.java:34-106`），
 * 所以"前后带空格的镜像名/描述"入库时会被裁掉——本实体由 {@code CicdRepository} 负责在写库前 `trim()`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CicdEntity {

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
