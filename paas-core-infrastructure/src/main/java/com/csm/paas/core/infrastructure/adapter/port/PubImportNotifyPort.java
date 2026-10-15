package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.node.adapter.port.IPubImportNotifyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Port 实现：本平台没有 `/topic/database` 通道，只记日志（C 类，见 {@link IPubImportNotifyPort}）。
 */
@Slf4j
@Component
public class PubImportNotifyPort implements IPubImportNotifyPort {

    @Override
    public void databaseImportStatus(boolean syncing, boolean success, int current, int total) {
        log.info("数据库导入进度（/topic/database 未接）: syncing={}, success={}, current={}, total={}",
                syncing, success, current, total);
    }
}
