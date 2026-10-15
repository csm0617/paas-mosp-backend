package com.csm.paas.core.domain.node.adapter.port;

/**
 * 一键导入的进度推送（厂商 `simpMessageSendingOperations.convertAndSend("/topic/database", dbImportStatus)`，
 * `PublicController:3564`、`:3571`）。
 *
 * <p><b>与厂商的差异（C 类）</b>：本平台的 STOMP 通道只有 `/topic/software`、`/topic/k8s`、`userInfo`
 * （见 `ISyncProgressNotifyPort` 的说明），**没有 `/topic/database`**，所以实现只记日志。
 * 若后续要接，只需在 trigger 里加一个 `SimpMessagingTemplate` 实现。
 */
public interface IPubImportNotifyPort {

    /**
     * 推送一次数据库导入进度。
     * <p>厂商推送的载荷是 `DbImportStatus{syncing, success, current, total}`。
     */
    void databaseImportStatus(boolean syncing, boolean success, int current, int total);
}
