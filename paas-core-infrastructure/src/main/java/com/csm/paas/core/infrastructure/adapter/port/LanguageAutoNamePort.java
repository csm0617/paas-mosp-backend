package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * i18n 自动名端口实现（厂商 `I18nUtil`：自动名落在 `g_setting`，键 `i18n.<module>.<language>.<key>`）。
 */
@Component
@RequiredArgsConstructor
public class LanguageAutoNamePort implements ILanguageAutoNamePort {

    private final ISettingRepository settingRepository;

    @Override
    public Optional<String> queryAutoName(String module, String language, String key) {
        if (module == null || language == null || key == null) {
            return Optional.empty();
        }
        // 厂商 `getAutoNameMessage`：值为空白（blank）也当没有
        return settingRepository.queryValue("i18n." + module + "." + language + "." + key)
                .filter(val -> !val.isBlank());
    }
}
