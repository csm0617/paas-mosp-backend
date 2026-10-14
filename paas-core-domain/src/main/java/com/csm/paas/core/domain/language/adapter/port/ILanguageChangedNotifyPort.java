package com.csm.paas.core.domain.language.adapter.port;

/**
 * 语言切换后通知其它组件（厂商 `KafkaProducerService.sendLanguageChanged` →
 * `kafkaTemplate.send("global_language", json)`）。
 *
 * <p>厂商把发送失败**吞掉**（只记日志）。我们的平台**不含 Kafka**，所以实现是一个"只记日志"的空实现——
 * 这是**外部依赖缺失**导致的差异（见 `docs/deviations.md` C 类），不是行为取舍。
 */
public interface ILanguageChangedNotifyPort {

    /** 语言已切换（`language` / `version` 是切换后的当前值）。 */
    void languageChanged(String language, String version);
}
