package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 导入执行结果（厂商 {@code service/sql/ExecResult.java}）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/sql/ExecResult.java:5-11`。
 * <p>照抄点：`success` 是**基本类型 boolean**；`POST /v1/database/{databaseName}/import` 成功时
 * 固定返回 `{success:true, error:null, cause:null}`（厂商 `DatabaseService.importSql:235-268`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseExecResultDTO {

    private boolean success;
    private String error;
    private String cause;
}
