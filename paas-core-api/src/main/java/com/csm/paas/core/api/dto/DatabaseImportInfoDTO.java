package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 导入包解析结果（厂商 {@code controller/response/DatabaseImportInfo.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/DatabaseImportInfo.java:7-9`。
 * <p>**不直接返回给前端**：`POST /v1/database/import` 只在服务端内部用它把
 * `databaseUserRelation.json` 与 `databaseSql.json` 传给导入流程
 * （厂商 `DataBaseController.importDatabaseInfo:328-337`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportInfoDTO {

    private List<DatabaseUserRelationInfoDTO> databaseUserRelationInfoList;
    private Map<String, String> sqlHashMap;
}
