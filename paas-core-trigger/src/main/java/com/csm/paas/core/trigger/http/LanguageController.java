package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.LanguageVersionInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
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
 * <p>其余端点（`/cur`、`/current`、`/import`、`POST /version`、`/detail`、`/remove`）尚未实现，
 * 见 `docs/findings/137` 的"剩余"一节。
 */
@RestController
@RequestMapping("/v1/lang")
@RequiredArgsConstructor
public class LanguageController {

    private final ILanguageQueryService languageQueryService;

    /**
     * 厂商 `GET /v1/lang/version?page=&size=&keyword=`：语言包版本列表（分页）。
     *
     * <p>关键字处理照抄：去空格 → `-` 换 `_` → 空则不过滤；排序 `create_time DESC`。
     * 规则见 `ILanguageQueryService.queryLanguageVersionList`（规格 `findings/137`）。
     */
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
