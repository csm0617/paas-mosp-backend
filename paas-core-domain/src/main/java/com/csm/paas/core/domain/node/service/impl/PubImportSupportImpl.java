package com.csm.paas.core.domain.node.service.impl;

import com.csm.paas.core.domain.backup.service.BackupLogHolder;
import com.csm.paas.core.domain.node.adapter.port.IPubImportNotifyPort;
import com.csm.paas.core.domain.node.service.IPubImportSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 一键导入外壳的实现（厂商 `PublicController.importFullBackUp:3369-3576` 的外围部分）。
 *
 * <p>只做三件事：恢复日志开关、`/topic/database` 进度推送（本平台无该通道 ⇒ 只记日志）、
 * 两个**未移植子导入**的显式缺口登记。zip 遍历与 JSON 解析按仓库既有分工放在 trigger 层
 * （见 `DataBaseController.inputStreamToFile:496-554` 的同样取舍）。
 */
@Slf4j
@Service
public class PubImportSupportImpl implements IPubImportSupport {

    /**
     * 厂商 `BackupLogService.RECOVERY` 的等价物。
     * <p>用 `volatile` 只是为了让日志线程能看到；语义仍是"进程内全局开关"（照抄静态量）。
     */
    private static volatile boolean recovery = false;

    @Resource
    private IPubImportNotifyPort pubImportNotifyPort;

    @Override
    public void beginImport() {
        recovery = true;
        // 厂商 `BackupLogService.recoveryLogs.clear()`
        BackupLogHolder.RECOVERY_LOGS.clear();
        sendRecoveryLog("gaop", "开始一键导入", null, "info");
    }

    @Override
    public void setRecovery(boolean recovery) {
        PubImportSupportImpl.recovery = recovery;
    }

    @Override
    public void sendRecoveryLog(String module, String message, String data, String level) {
        if (!recovery) {
            log.debug("恢复日志开关未打开，丢弃一条恢复日志: {} / {}", module, message);
            return;
        }
        BackupLogHolder.sendRecoveryLog(module, message, data, level);
    }

    @Override
    public void sendDatabaseImportProgress(boolean syncing, boolean success, int current, int total) {
        pubImportNotifyPort.databaseImportStatus(syncing, success, current, total);
    }

    @Override
    public void importSoftwareJson(String json) {
        log.warn("software.json 导入未移植（SoftwareService.importSoftwareInfo 属 domain/software，"
                + "本批白名单不放行），跳过 {} 字节", json == null ? 0 : json.length());
        sendRecoveryLog("software", "software.json 导入未移植，已跳过（详见 IPubImportSupport 说明）",
                null, "error");
    }

    @Override
    public void importDatabaseUserJson(byte[] json) {
        log.warn("databaseUser.json 导入未移植（g_database_user 未建模），跳过 {} 字节",
                json == null ? 0 : json.length);
        sendRecoveryLog("database", "databaseUser.json 导入未移植，已跳过（g_database_user 未建模）",
                null, "error");
    }
}
