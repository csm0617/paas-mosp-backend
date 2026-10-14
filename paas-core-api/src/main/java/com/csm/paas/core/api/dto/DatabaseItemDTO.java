package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务数据库一行（厂商 {@code controller/response/database/DatabaseItem.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/DatabaseItem.java:6-18`。
 * <p>照抄点：`name`/`tableCount` 来自**MySQL 管理连接**的 `information_schema` 聚合
 * （厂商 `MysqlMapper.allDatabasesTableCount`）；`service`/`desc`/`sqlName`/`userName`
 * 来自本地 `g_database` 表**逐字段补齐**（厂商 `DatabaseService.mapDatabaseDesc:655-681`：
 * 只在库里有非 null 值时才覆盖，否则保留 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseItemDTO {

    private String name;
    private Long tableCount;
    private String service;
    private String desc;
    private String sqlName;
    private String userName;
}
