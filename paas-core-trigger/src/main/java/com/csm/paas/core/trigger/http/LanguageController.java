package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.LanguageInfoDTO;
import com.csm.paas.core.api.dto.LanguageVersionInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.language.service.ILanguageCommandService;
import com.csm.paas.core.domain.language.adapter.port.ILanguageChangedNotifyPort;
import com.csm.paas.core.domain.language.service.ILanguageQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 厂商 `LanguageController`（`/v1/lang`）。
 *
 * <p>已实现 `GET /version`（`findings/137`）、`DELETE /remove`（`findings/138`）、
 * `GET /detail`（`findings/139`）、`POST /version`（`findings/140`）、`POST /import`（本轮）；
 * 其余端点（`/cur`、`/current`）尚未实现。
 */
@Slf4j
@RestController
@RequestMapping("/v1/lang")
@RequiredArgsConstructor
public class LanguageController {

    private final ILanguageQueryService languageQueryService;
    private final ILanguageCommandService languageCommandService;
    private final ILanguageChangedNotifyPort languageChangedNotifyPort;

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

    /**
     * 厂商 `GET /v1/lang/detail?lang=&version=&module=`：语言包详情。
     *
     * <p>`version` 为空 ⇒ 用当前版本；`version=default` ⇒ 读**内置**语言包（`classpath:i18n/messages*.properties`）；
     * 否则查 `g_language`（`module` 非空时再按 module 过滤）。规则见
     * `ILanguageQueryService.queryLanguageDetail`（规格 `findings/139`）。
     */
    @GetMapping("/detail")
    public ResponseDTO<LanguageInfoDTO> langDetail(
            @RequestParam String lang,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String module) {
        var info = languageQueryService.queryLanguageDetail(lang, version, module);
        return ResponseDTO.ok(LanguageInfoDTO.builder()
                .languageList(info.getLanguageList().stream()
                        .map(e -> LanguageInfoDTO.LanguageEntryDTO.builder()
                                .id(e.getId()).description(e.getDescription()).textKey(e.getTextKey())
                                .module(e.getModule()).content(e.getContent()).abbr(e.getAbbr())
                                .build())
                        .toList())
                .total(info.getTotal()).module(info.getModule())
                .lang(info.getLang()).langVersion(info.getLangVersion())
                .build());
    }

    /**
     * 厂商 `POST /v1/lang/version?lang=&version=`：设置某语言的当前版本。
     *
     * <p>`version=default` 时会先删掉 `./i18n` 目录（切回内置资源）；**成功后**再
     * 把当前语言包导出成 `./i18n/messages_<lang>.properties`，并（厂商侧）发 Kafka 通知。
     * 规则见 `ILanguageCommandService.setLanguageVersion`（规格 `findings/140`）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/version")
    public ResponseDTO<Boolean> setLangVersion(
            @RequestParam String lang, @RequestParam String version) throws java.io.IOException {
        boolean updated = languageCommandService.setLanguageVersion(lang, version);
        if (updated) {
            // 厂商：成功后导出当前语言包到 ./i18n（用切换后的当前语言/版本）
            var current = languageQueryService.queryCurrent();
            var info = languageQueryService.queryLanguageDetail(
                    current.getLanguage(), current.getVersion(), "gaop");
            com.csm.paas.core.trigger.assembler.LanguagePropertiesWriter.write(
                    current.getLanguage(), info.getLanguageList());
            languageChangedNotifyPort.languageChanged(current.getLanguage(), current.getVersion());
        }
        return ResponseDTO.ok(updated);
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

    /**
     * 厂商 `POST /v1/lang/import`（multipart `version`(可选) + `file`）：语言包导入。
     *
     * <p>厂商 `LanguageController.java:51-56` → `LanguageServiceImpl.importLanguageExcel:90-259`。
     * 照抄点：**没有 MIME 守卫**（直接交给 POI）；`WorkbookFactory.create` 抛 IOException ⇒
     * 400 `language.parse.language.package.exception`；词条为空 ⇒ 200 `data:false`（不写库）；
     * 否则算版本号、按 module 查重（重复 ⇒ 400 `language.import.duplicate.key`）、每 4000 条一批插入，
     * 返回 200 `data:true`。规则见 {@code ILanguageCommandService.importLanguageExcel}。
     */
    @org.springframework.web.bind.annotation.PostMapping(value = "/import",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Boolean> importLanguageExcel(
            @RequestParam(required = false) String version,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        com.csm.paas.core.domain.language.model.valobj.LanguageImportParseResultVO parsed;
        try {
            parsed = com.csm.paas.core.trigger.assembler.LanguageImportExcelParser.parse(file.getInputStream());
        } catch (java.io.IOException e) {
            // 厂商 LanguageServiceImpl:105-108
            log.info("导入语言包出现异常", e);
            throw new com.csm.paas.core.types.exception.WrongArgumentException(
                    "language.parse.language.package.exception");
        }
        return ResponseDTO.ok(languageCommandService.importLanguageExcel(version, parsed));
    }
}
