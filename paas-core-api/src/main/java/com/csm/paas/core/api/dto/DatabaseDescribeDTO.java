package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `PUT /v1/database/{dataBaseName}` 的请求体（厂商 {@code controller/response/database/DatabaseDescribe.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/DatabaseDescribe.java:6-12`。
 * <p>注意：这三个字段是**可选**的——厂商 `DatabaseService.updateDatabaseByName:592-602` 直接把
 * null 写进 `g_database`（`updateDatabaseByName` 的 SQL 是 `set desc=#{desc}, service=#{service}`），
 * 不做存在性合并。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseDescribeDTO {

    private String service;
    private String desc;
    private String userName;
}
