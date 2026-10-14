package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.LanguageVersionInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.language.service.ILanguageCommandService;
import com.csm.paas.core.domain.language.service.ILanguageQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 厂商 `LanguageController`（`/v1/lang`）——本轮实现其中的 **`GET /version`**。
 *
 * <p>已实现 `GET /version`（`findings/137`）与 `DELETE /remove`（`findings/138`）；
 * 其余端点（`/cur`、`/current`、`/import`、`POST /version`、`/detail`）尚未实现。
 */
@RestController
@RequestMapping("/v1/lang")
@RequiredArgsConstructor
public class LanguageController {

    private final ILanguageQueryService languageQueryService;
    private final ILanguageCommandService languageCommandService;

    /**
     * 厂商 `GET /v1/lang/version?page=&size=&keyword=`：语言包版本列表（分页）。
     *
     * <p>关键字处理照抄：去空格 → `-` 换 `_` → 空则不过滤；排序 `create_time DESC`。
     * 规则见 `ILanguageQueryService.queryLanguageVersionList`（规格 `findings/137`）。
     */
    /**
     * 厂商 `DELETE /v1/lang/remove?lang=&version=`：删除某个语言包版本。
     *
     * <p>`version=default` ⇒ `data:false`（不允许删默认版本）；该版本不存在 ⇒ `data:true`（幂等）；
     * 否则先删词条再删版本行。规则见 `ILanguageCommandService.deleteLanguageVersion`（规格 `findings/138`）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/remove")
    public ResponseDTO<Boolean> removeLangVersion(
            @RequestParam String lang, @RequestParam String version) {
        return ResponseDTO.ok(languageCommandService.deleteLanguageVersion(lang, version));
    }

    @GetMapping("/version")
    public ResponseDTO<LanguageVersionInfoDTO> langVersionList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        var result = languageQueryService.queryLanguageVersionList(
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        List<LanguageVersionInfoDTO.LanguageVersionDTO> list = result.getVersionList().stream()
                .map(v -> LanguageVersionInfoDTO.LanguageVersionDTO.builder()
                        .id(v.getId()).lang(v.getLang()).langVersion(v.getLangVersion())
                        .used(v.getUsed()).createTime(v.getCreateTime())
                        .build())
                .toList();
        return ResponseDTO.ok(LanguageVersionInfoDTO.builder().versionList(list).total(result.getTotal()).build());
    }
}
