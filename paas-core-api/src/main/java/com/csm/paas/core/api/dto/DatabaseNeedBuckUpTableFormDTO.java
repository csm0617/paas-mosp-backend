package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `POST /v1/database/backup_db_data_sync` 的请求体
 * （厂商 {@code controller/request/NeedBuckUpTableForm.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/request/NeedBuckUpTableForm.java:9-14`。
 * <p>照抄点：`databaseName` 带 `@NotBlank`（本重写保留校验注解语义，但厂商**控制器没有 @Valid**，
 * 所以该注解实际不生效——`CollectionUtils.isEmpty(forms)` 才是唯一的入口校验）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseNeedBuckUpTableFormDTO {

    private String databaseName;
    private List<String> tableNames;
}
