package com.csm.paas.core.domain.sync.service.impl;

import com.csm.paas.core.domain.sync.model.SyncStatusHolder;
import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;
import com.csm.paas.core.domain.sync.service.ISyncQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 同步状态：与厂商一样用**一个进程内单例**保存（厂商 `SyncController.syncStatus` 是 static）。
 * <p>`POST /v1/sync` 现在也读写同一个单例（见 {@link SyncStatusHolder} 与
 * `SyncCommandServiceImpl`），所以 `GET /status` 能看到同步中的进度。
 */
@Slf4j
@Service
public class SyncQueryServiceImpl implements ISyncQueryService {

    @Override
    public SyncStatusEntity queryStatus() {
        return SyncStatusHolder.INSTANCE;
    }
}
