package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.CurrentLanguageDTO;
import com.csm.paas.core.api.dto.ModeDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SettingDTO;
import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;
import com.csm.paas.core.domain.language.service.ILanguageQueryService;
import com.csm.paas.core.domain.mode.service.IModeQueryService;
import com.csm.paas.core.domain.setting.model.entity.SettingEntity;
import com.csm.paas.core.domain.setting.service.ISettingQueryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 页面**外壳**所需的只读端点：前端每次加载页面都会调它们
 * （`docs/findings/13-frontend-e2e-first-run.md` 的实测清单）。
 * <p>刻意不做登录校验：它们是只读平台数据，且前端在登录页也会用到语言/设置；
 * 真正的鉴权在网关侧（见 docs/findings/12-auth-contract.md）。这一点记在工单 07/08 待追认。
 */
@RestController
public class ShellController {

    @Resource
    private ISettingQueryService settingQueryService;

    @Resource
    private IModeQueryService modeQueryService;

    @Resource
    private ILanguageQueryService languageQueryService;

    /** 厂商 GET /settings：全量设置（主键序）。 */
    @GetMapping("/settings")
    public ResponseDTO<List<SettingDTO>> settings() {
        return ResponseDTO.ok(settingQueryService.queryAll().stream()
                .map(this::toSettingDTO).toList());
    }

    /** 厂商 GET /v1/mode/current：`used=1` 的作战模式。 */
    @GetMapping("/v1/mode/current")
    public ResponseDTO<ModeDTO> currentMode() {
        return modeQueryService.queryCurrent()
                .map(m -> ResponseDTO.ok(toModeDTO(m)))
                .orElseGet(() -> ResponseDTO.ok(null));
    }

    /** 厂商 GET /v1/mode/list：全量作战模式。 */
    @GetMapping("/v1/mode/list")
    public ResponseDTO<List<ModeDTO>> modeList() {
        return ResponseDTO.ok(modeQueryService.queryAll().stream().map(this::toModeDTO).toList());
    }

    /**
     * 厂商 `GET /v1/pub/quickStart`：快速开始弹窗是否不再弹出。
     * 语义照厂商 `PublicController.getQuickStartStatus()`：读 `g_setting['system.init.quick_start']`，非 "true" 即 false。
     */
    @GetMapping("/v1/pub/quickStart")
    public ResponseDTO<Boolean> quickStart() {
        return ResponseDTO.ok(settingQueryService.queryBooleanValue("system.init.quick_start"));
    }

    /** 厂商 GET /v1/lang/current：当前语言与语言包版本。 */
    @GetMapping("/v1/lang/current")
    public ResponseDTO<CurrentLanguageDTO> currentLanguage() {
        CurrentLanguageEntity current = languageQueryService.queryCurrent();
        return ResponseDTO.ok(CurrentLanguageDTO.builder()
                .language(current.getLanguage()).version(current.getVersion()).build());
    }

    private SettingDTO toSettingDTO(SettingEntity s) {
        return SettingDTO.builder()
                .key(s.getKey()).updatedAt(s.getUpdatedAt()).createdAt(s.getCreatedAt())
                .val(s.getVal()).desc(s.getDesc())
                .build();
    }

    private ModeDTO toModeDTO(com.csm.paas.core.domain.mode.model.entity.ModeEntity m) {
        return ModeDTO.builder()
                .id(m.getId()).type(m.getType()).mode(m.getMode())
                .used(m.getUsed()).createTime(m.getCreateTime()).modeName(m.getModeName())
                .build();
    }
}
