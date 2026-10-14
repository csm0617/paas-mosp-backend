package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MySQL 全局配置（厂商 {@code controller/response/database/MysqlGlobalSettings.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/MysqlGlobalSettings.java:6-20`。
 * <p>照抄点：厂商字段名是 **`Port`（大写 P）**，但 getter 是 `getPort()` ⇒ Jackson 产出的键是
 * **`port`（小写）**；这里直接把 Java 字段命名为 `port`，序列化结果与厂商一致。
 * <p>厂商 `DatabaseService.getAllMysqlGlobalSettings:841-870` 的几条怪点照抄：
 * `storageSize` 的人读单位换算（≤1024 MB → `N MB`，否则 `N GB`，≥1024 GB 再改成 `N TB`——
 * 第三次换算后**前缀是 GB 那个值再除 1024**，`mysqlSize >= 1024` 时又被覆盖成 TB）；
 * `getFromMap` 取不到时返回**空串**而不是 null。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseMysqlInfoDTO {

    private String storageEngine;
    private String storageSize;
    private String timeZone;
    private String caseSensitive;
    private String port;
    private String version;
    private String createdAt;
}
