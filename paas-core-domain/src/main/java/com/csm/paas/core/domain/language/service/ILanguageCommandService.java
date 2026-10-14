package com.csm.paas.core.domain.language.service;

/**
 * 语言包写操作（厂商 `LanguageController` 的 `POST /version`、`DELETE /remove`、`POST /import`）。
 */
public interface ILanguageCommandService {

    /**
     * 删除某个语言包版本（厂商 `DELETE /v1/lang/remove?lang=&version=` →
     * `LanguageServiceImpl.deleteLangVersion:349-362`，规格见 `docs/findings/138`）。
     *
     * <p>规则：
     * <ol>
     *   <li>`lang` 先做 `-` → `_` 归一化（`BaseLanguageEnum.lang2Underline`）；</li>
     *   <li>**`version` 是 `default` ⇒ 直接返回 false**（不允许删默认版本）；</li>
     *   <li>该语言+版本**不存在 ⇒ 返回 true**（幂等）；</li>
     *   <li>否则：先删 `g_language` 里该语言+版本的**全部词条**，再按主键删 `g_language_version` 那一行，
     *       **返回"版本行是否真的删掉了"**（`delete > 0`）。</li>
     * </ol>
     */
    boolean deleteLanguageVersion(String lang, String version);

    /**
     * 设置某语言的当前版本（厂商 `POST /v1/lang/version?lang=&version=` →
     * `LanguageServiceImpl.setLanguageVersion:421-437`，规格见 `docs/findings/140`）。
     *
     * <p>规则：
     * <ol>
     *   <li>`lang` 或 `version` 为 null ⇒ **false**；`lang` 先 `-` → `_` 归一化；</li>
     *   <li>`version == "default"` ⇒ 先 `updateLanguageVersion("default", lang)`
     *       （**全局**把所有 `used=1` 清 0，再把 (lang, default) 置 1），再走
     *       {@code setDefaultVersionLang}：**非基础语言（zh_CN/en_US/fr_FR）⇒ false**；
     *       `./i18n` 不存在 ⇒ true（只记日志）；存在 ⇒ **删掉整个目录**后 true；</li>
     *   <li>其它 version ⇒ 先 `count` 该 (lang, version) 行数，**为 0 ⇒ false**；
     *       否则同样"先清 0 再置 1"，返回**置 1 的受影响行数 > 0**。</li>
     * </ol>
     */
    boolean setLanguageVersion(String lang, String version);

    /**
     * 语言包导入（厂商 `POST /v1/lang/import` → `LanguageServiceImpl.importLanguageExcel:90-259`）。
     *
     * <p>POI 解析在 trigger（{@code LanguageImportExcelParser}），这里只做厂商那段"算版本号 + 查重 + 落库"：
     * <ol>
     *   <li>`rows` 为空（等价厂商 `lastRowNum < 1`）⇒ 记日志并返回 **false**，不写任何库；</li>
     *   <li>`version` 空白 ⇒ 查该 `lang` **当天**（本地时区 00:00:00.000–23:59:59.999）已有版本行数 `size`
     *       ⇒ 版本号 `yyyyMMdd + "V" + (size + 1)`，`createTime=now`、`used=0`；</li>
     *   <li>`version` 非空白 ⇒ 版本号就用传入值；查 (lang, version) **不存在**时才补
     *       `createTime=now`/`used=0`；**已存在时那条版本对象只剩 `lang`，但仍然会 insertSelective**
     *       （厂商怪点，照抄）；</li>
     *   <li>{@code checkDuplicateKeyGroupByModule}：按 `module` 分组找**重复 textKey**，
     *       有重复 ⇒ 400 `language.import.duplicate.key`，参数形如 `module:k1,k2`（多 module 用空格分隔）；</li>
     *   <li>每 4000 条一批 `insertBatch`，最后插版本行，返回 **true**。</li>
     * </ol>
     *
     * @param version 前端可选传入的版本号（空白等价于没传）
     * @param parsed  trigger 解析结果（含生效 lang 与词条行）
     */
    boolean importLanguageExcel(String version,
            com.csm.paas.core.domain.language.model.valobj.LanguageImportParseResultVO parsed);
}
