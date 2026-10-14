package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库 + 表数 + 展示字段（厂商 {@code controller/response/database/DatabaseItem}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.getUserTableCount:604-617` +
 * `mapDatabaseDesc:655-681`：`name`/`tableCount` 来自 MySQL 管理连接，
 * `service`/`desc`/`sqlName`/`userName` 由本地 `g_database` **按库名补齐**（取不到就保持 null）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseItemEntity {

    private String name;
    private Long tableCount;
    private String service;
    private String desc;
    private String sqlName;
    private String userName;
}
