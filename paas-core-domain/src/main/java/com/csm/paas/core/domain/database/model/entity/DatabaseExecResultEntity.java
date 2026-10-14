package com.csm.paas.core.domain.database.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 执行/导入结果（厂商 {@code service/sql/ExecResult} + {@code service/database/ExecSqlResult}）。
 *
 * <p>厂商出处：
 * <ul>
 *   <li>`service/sql/ExecResult.java:5-11`：`success`/`error`/`cause` 三键——**这是对外形状**
 *       （`POST /v1/database/{databaseName}/import` 的 data）。</li>
 *   <li>`service/database/ExecSqlResult`：内部实现用的 builder，**多出 `log` 与 `exitCode`**
 *       （`DatabaseService.executeSqlToDatabase:401-422` 会带 `exitCode`/`log` /
 *       `exception`）。`log`/`exitCode` **不进对外响应**，只用于写 `g_database_record.execFaultMessage`。</li>
 * </ul>
 * <p>照抄点：厂商 `executeSqlToDatabase` 的 catch 分支都忘了用 builder 的返回值，
 * 最终无条件 `return result.exitCode(0).log(...).success(true).build()` ⇒ **几乎总是 success=true**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseExecResultEntity {

    private boolean success;
    private String error;
    private String cause;

    /** 厂商 `ExecSqlResult.log`：mysql 子进程的输出（失败时非空）。 */
    private String log;

    /** 厂商 `ExecSqlResult.exitCode`：进程退出码（超时/未结束时厂商写 -1）。 */
    private Integer exitCode;
}
