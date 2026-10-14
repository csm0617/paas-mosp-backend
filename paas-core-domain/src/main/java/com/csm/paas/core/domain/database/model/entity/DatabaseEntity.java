package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_database` 的一行（厂商 {@code model/Database.java:5-12}，6 列）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/Database.java`；
 * 列名见 `dao/DatabaseDynamicSqlSupport.java:21-26`：
 * `id, \`database_name\`, \`username\`, \`desc\`, \`sql_name\`, \`service\``——
 * 后四列在厂商的 dynamic-sql 里**带反引号**（都是 MySQL 关键字或保留字）。
 *
 * <p>照抄点：厂商的 setter 对每个字符串字段都做 **`trim()`**
 * （`databaseName == null ? null : databaseName.trim()`），本次重写把该规整放在仓储层
 * （与厂商"setter 即规整"等价，因为厂商所有写入路径都走 setter）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseEntity {

    private Long id;
    private String databaseName;
    private String username;
    private String desc;
    private String sqlName;
    private String service;
}
