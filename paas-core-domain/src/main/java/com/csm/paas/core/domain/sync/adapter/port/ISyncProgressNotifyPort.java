package com.csm.paas.core.domain.sync.adapter.port;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;

/**
 * 同步进度推送（厂商 `simpMessageSendingOperations.convertAndSend("/topic/sync", syncStatus)`，
 * `SyncController.java:263`、`:334`、`:458`、`:475` 等处）。
 *
 * <p><b>与厂商的差异（C 类）</b>：本平台目前**没有接 `/topic/sync` 这个通道**
 * （`trigger/websocket` 下只有 `/topic/software`、`/topic/k8s`、`userInfo` 三个通道），
 * 所以实现只记日志。若后续要接，只需在 trigger 里加一个 `SimpMessagingTemplate` 实现。
 */
public interface ISyncProgressNotifyPort {

    /** 把当前同步状态推给前端。 */
    void syncProgress(SyncStatusEntity status);
}
