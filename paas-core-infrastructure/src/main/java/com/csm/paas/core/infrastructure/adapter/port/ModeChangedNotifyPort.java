package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.mode.adapter.port.IModeChangedNotifyPort;
import com.csm.paas.core.domain.mode.model.entity.ModeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模式切换通知的**空实现**：厂商发 Kafka 主题 `global_mode`（失败只记日志）；
 * 本平台不含 Kafka，所以只记一行日志，保持"主流程不受影响"的语义。
 *
 * <p><b>类 C：本环境不可验证</b>——没有 Kafka。
 */
@Slf4j
@Component
public class ModeChangedNotifyPort implements IModeChangedNotifyPort {

    @Override
    public void modeChanged(ModeEntity mode) {
        log.info("作战模式已切换（本平台无 Kafka，仅记日志）: mode={}, id={}",
                mode == null ? null : mode.getMode(), mode == null ? null : mode.getId());
    }
}
