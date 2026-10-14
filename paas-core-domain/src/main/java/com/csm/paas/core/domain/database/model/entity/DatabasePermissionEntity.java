package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户对库的访问权限（厂商 {@code controller/response/database/DatabasePermission}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../dao/mysql/MysqlMapper.databasesAccessControl`
 * ——一条 `GRANT ALL ON ... to ...` 被写成 `@Select`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabasePermissionEntity {

    private String databaseName;
    private String accessUser;
}
