package com.csm.paas.core.domain.node.service;

/**
 * 一键导入（`POST /v1/pub/import`）的**外壳支撑**：恢复日志开关与两处"本仓未移植"的子导入。
 *
 * <p>放在 `domain/node` 下同 {@code IPubServiceQueryService} 的理由（文件白名单）。
 *
 * <p>厂商出处：`controller/pub/PublicController.importFullBackUp:3369-3576`——
 * 方法体开头就是 {@code BackupLogService.RECOVERY = true} + {@code recoveryLogs.clear()}，
 * 之后每个条目都会 {@code sendRecoveryLog(...)}；而 {@code sendRecoveryLog} 本身
 * **只有在 {@code RECOVERY == true} 时才真正记录**（`BackupLogService.sendRecoveryLog:52-57`）。
 *
 * <p><b>与厂商的差异（B 类，主动偏离）</b>：厂商的 `RECOVERY` 是 `BackupLogService` 上的全局静态量；
 * 本仓的 `BackupLogHolder`（backup 域，**本次白名单不放行**）没有这个开关、`sendRecoveryLog` 无条件追加。
 * 所以本支撑类自己维护一个开关，只在 `true` 时转发给 `BackupLogHolder`——
 * 对**本端点**的可见行为与厂商一致；其它端点若直接调 `BackupLogHolder.sendRecoveryLog`，
 * 仍会无条件追加（那是既有实现的既有行为，不属本批）。
 */
public interface IPubImportSupport {

    /** 厂商 `PublicController:3378-3380`：`RECOVERY = true` + 清空恢复日志 + 记一条"开始一键导入"。 */
    void beginImport();

    /**
     * 厂商在**错误分支**里把 `RECOVERY` 置回 false（`:3508`、`:3544`）。
     * <p>注意厂商**成功后不置回**（`:3575` 直接 `return Response.ok(true)`）——这个"忘了复位"照抄。
     */
    void setRecovery(boolean recovery);

    /**
     * 厂商 `BackupLogService.sendRecoveryLog(module, message, data, level)` 的门面：
     * 只在恢复开关为 true 时写入 `BackupLogHolder.RECOVERY_LOGS`（供 `GET /v1/backup/recoveryLogs` 读）。
     */
    void sendRecoveryLog(String module, String message, String data, String level);

    /** 厂商 `simpMessageSendingOperations.convertAndSend("/topic/database", status)`（`:3564`、`:3571`）。 */
    void sendDatabaseImportProgress(boolean syncing, boolean success, int current, int total);

    /**
     * 厂商 `PublicController:3461-3462` 的 `softwareService.importSoftwareInfo(...)`。
     *
     * <p><b>本仓缺口</b>：`SoftwareService.importSoftwareInfo:1195-...`（几百行，含 Deployment 重建、
     * 镜像地址重写、节点绑定、白名单等）**尚未移植**，且它属于 `domain/software/**`——
     * 本次任务的白名单**不放行**该包。这里如实记 WARN + 一条 `error` 级恢复日志后**跳过**，
     * 不伪造成功（与 `IBaseDataExportPort` 记录"尚未移植"的做法一致）。
     */
    void importSoftwareJson(String json);

    /**
     * 厂商 `PublicController:3485` 的 `databaseUserService.importDatabaseUser(in)`。
     *
     * <p><b>本仓缺口</b>：`g_database_user` 未建模（`DatabaseServiceImpl` 类注释已如实记录），
     * 且 `DatabaseUserService` 在另一包。这里照 `DataBaseController.inputStreamToFile:526-530`
     * 的既有做法：**只记日志跳过**。
     */
    void importDatabaseUserJson(byte[] json);
}
