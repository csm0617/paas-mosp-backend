package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某个用户对某个库的访问权限（厂商 {@code controller/response/database/DatabasePermission.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../controller/response/database/DatabasePermission.java:6-9`。
 * <p>照抄点（**怪点**）：厂商 `MysqlMapper.databasesAccessControl` 的 SQL 是
 * `GRANT ALL ON \`{db}\`.* to \`{user}\`@'%'`，却用 **`@Select`**（即 `executeQuery`）调用，
 * 并把这个"授权动作"当成查询来"返回 `List&lt;DatabasePermission&gt;`"——
 * 若不报错，结果集里**只有被授权的用户与库的文本**，`accessUser` 实际来自 SQL 里的字面量。
 * 本重写照抄该 SQL 与调用方式。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabasePermissionDTO {

    private String databaseName;
    private String accessUser;
}
