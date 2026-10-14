package com.csm.paas.core.domain.mode.adapter.port;

import com.csm.paas.core.domain.mode.model.entity.ModeEntity;

/**
 * 作战模式切换后的通知（厂商 `KafkaProducerService.sendModeChanged` →
 * `kafkaTemplate.send("global_mode", JsonUtils.toStr(mode))`，`KafkaProducerService.java:34-41`）。
 *
 * <p>厂商把发送失败**吞掉**（只记日志）。本平台**不含 Kafka**，实现是"只记日志"的空实现
 * （见 `infrastructure/adapter/port/ModeChangedNotifyPort`）——外部依赖缺失，属 C 类差异。
 */
public interface IModeChangedNotifyPort {

    /** 模式已切换（传切换后的当前模式，厂商传的是 `getCurrentMode()` 的结果）。 */
    void modeChanged(ModeEntity mode);
}
