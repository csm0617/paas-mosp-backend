package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 导入包解析结果（厂商 {@code controller/response/DatabaseImportInfo}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.inputStreamToFile:1310-1410`
 * ——遍历 zip，把 `databaseUserRelation.json`（Gson 反序列化）与 `databaseSql.json`
 * （`Map&lt;sqlName, dbName&gt;`）收集出来，其余 `.sql`/`.zip` 原样转存到一个临时 zip。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportInfoEntity {

    private List<DatabaseUserRelationInfoEntity> databaseUserRelationInfoList;
    private Map<String, String> sqlHashMap;
}
