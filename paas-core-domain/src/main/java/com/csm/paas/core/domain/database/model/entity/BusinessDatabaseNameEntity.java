package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务库「名字 + 表数」——厂商 `MysqlMapper.allDatabasesTableCount` 的返回行
 * （映射到 `controller/response/database/DatabaseItem` 的 `name`/`tableCount` 两个字段）。
 *
 * <p>厂商出处 SQL（`dao/mysql/MysqlMapper.java`，`@DataSource(DbType.MYSQL)` + `@Select`）：
 * <pre>
 * select count(t.table_name) as tableCount, s.schema_name as name
 * from information_schema.SCHEMATA as s
 * left join information_schema.`TABLES` t on t.table_schema = s.schema_name
 * where s.schema_name not in ('gaop','sys','information_schema','performance_schema','mysql')
 * [and s.schema_name like concat('%',#{keyword},'%')]
 * GROUP BY name ORDER BY name
 * </pre>
 * 照抄点：`gaop` 库被**排除**、按名字排序、非空 keyword 才做 like（`&lt;when test='keyword!=null'&gt;`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessDatabaseNameEntity {

    private String name;
    private Long tableCount;
}
