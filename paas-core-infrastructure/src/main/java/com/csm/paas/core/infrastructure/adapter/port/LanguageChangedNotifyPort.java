package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.language.adapter.port.ILanguageChangedNotifyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 语言切换通知的**空实现**：厂商发 Kafka 主题 `global_language`（失败只记日志）；
 * 我们的平台不含 Kafka，所以只记一行日志，保持"主流程不受影响"的语义。
 */
@Slf4j
@Component
public class LanguageChangedNotifyPort implements ILanguageChangedNotifyPort {

    @Override
    public void languageChanged(String language, String version) {
        log.info("语言已切换（本平台无 Kafka，仅记日志）: language={}, version={}", language, version);
    }
}
