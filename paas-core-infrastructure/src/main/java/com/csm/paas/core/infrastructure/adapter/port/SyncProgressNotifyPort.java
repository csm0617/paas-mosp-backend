package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.sync.adapter.port.ISyncProgressNotifyPort;
import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 同步进度通知的**只记日志实现**。
 *
 * <p>厂商推 WebSocket `/topic/sync`（`SyncController.java:263` 等）；本平台目前没有接这个通道
 * （`trigger/websocket` 下只有 `/topic/software`、`/topic/k8s`、`userInfo`），
 * 所以这里只记日志——**C 类差异**（外部通道未接），不是行为取舍。
 */
@Slf4j
@Component
public class SyncProgressNotifyPort implements ISyncProgressNotifyPort {

    @Override
    public void syncProgress(SyncStatusEntity status) {
        log.info("同步进度（本平台未接 /topic/sync，仅记日志）: syncing={}, success={}, type={}, name={}, {}/{}, error={}",
                status.isSyncing(), status.isSuccess(), status.getCurrentSyncType(),
                status.getCurrentSyncName(), status.getCurrent(), status.getTotal(), status.getErrorStatus());
    }
}
