package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MySQL 全局配置（厂商 {@code controller/response/database/MysqlGlobalSettings}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../DatabaseService.getAllMysqlGlobalSettings:841-870`。
 * <p>`port` 对应厂商的 Java 字段 `Port`（getter `getPort()` ⇒ JSON 键 `port`）。
 * <p>照抄点：`getFromMap` 取不到返回**空串**（不是 null）；`storageSize` 的三段换算见
 * `DatabaseService.java:844-855`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MysqlGlobalSettingsEntity {

    private String storageEngine;
    private String storageSize;
    private String timeZone;
    private String caseSensitive;
    private String port;
    private String version;
    private String createdAt;
}
