package com.csm.paas.core.domain.image.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 镜像 tag（厂商 `third/response/harbor/tags/v2/TagItem`）。
 *
 * <p>字段序=厂商声明序（`artifactId`→`id`→`immutable`→`name`→`pullTime`→`pushTime`→`repositoryId`
 * →`signed`→`workLoad`），trigger 的 assembler 按此序输出 JSON。
 *
 * <p>厂商的 `pushTime` 是 `java.util.Date`（`Instant.ofEpochMilli(created)`），
 * 由 Jackson 按 `spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` + `time-zone: UTC` 输出
 * （与 paas-core `application.yml:15-19` 一致）。
 * <p>`workLoad` 字段初值为 0，`getId`/`getRepositoryId`/`getImmutable`/`getSigned` 在 K3s 实现里被
 * 显式写成同一个自增序号 / `false`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TagItemVO {

    private Integer artifactId;
    private Integer id;
    private Boolean immutable;
    private String name;
    private Date pullTime;
    private Date pushTime;
    private Integer repositoryId;
    private Boolean signed;
    private Integer workLoad;
}
