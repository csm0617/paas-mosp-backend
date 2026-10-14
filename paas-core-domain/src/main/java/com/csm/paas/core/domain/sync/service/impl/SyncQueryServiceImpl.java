package com.csm.paas.core.domain.sync.service.impl;

import com.csm.paas.core.domain.sync.model.entity.SyncStatusEntity;
import com.csm.paas.core.domain.sync.service.ISyncQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 同步状态：与厂商一样用**一个进程内单例**保存（厂商 `SyncController.syncStatus` 是 static）。
 * <p>因为 `POST /v1/sync` 未实现，这个状态目前只会是初始值（规格见 `docs/findings/153`）。
 */
@Slf4j
@Service
public class SyncQueryServiceImpl implements ISyncQueryService {

    private final SyncStatusEntity syncStatus = SyncStatusEntity.init();

    @Override
    public SyncStatusEntity queryStatus() {
        return syncStatus;
    }
}
