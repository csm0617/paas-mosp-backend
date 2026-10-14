# `GET /v1/lang/detail` 规格 + 实现（第 146 轮）

**证据等级**：〔代码事实〕（厂商 `LanguageController:66-70` + `LanguageServiceImpl.getLangList:284-294` /
`getLangListFromProperties:296-343` / `getLanguages:485-490` / `createLanguageInfoFromLanguageEntities:506-513` 逐行读）
+〔运行实测〕（真库 + **与本地 properties 逐键逐值对账**）。2026-10-14。

## 1. 端点

```java
@GetMapping("/detail")
public Response<LanguageInfo> getLangDetail(@RequestParam String lang,
                                            @RequestParam(required=false) String version,
                                            @RequestParam(required=false) String module)
```
`LanguageInfo{languageList, total, module, lang, langVersion}`，
`LanguageEntity{id, description, textKey, module, content, abbr}`。

## 2. 流程

```
lang = lang2Underline(lang)                                  // `-` → `_`
if (version 为空白) version = 当前版本                        // used=1 那行的 lang_version；没有则 "default"
if (version == "default"):
    list = 读内置语言包 classpath:messages*.properties        // 仅基础语言（zh_CN/en_US/fr_FR）
           每条映射成 {textKey, content, module="gaop", langVersion="default"}（id/description/abbr 为 null）
    return {languageList=list, total=list.size(), module=**"gaop"**, lang, langVersion="default"}
否则:
    list = g_language where lang=? and lang_version=? [and module=?]   // module 空白 ⇒ 不过滤
    return {languageList=list, total=list.size(), module=**入参 module**, lang, langVersion=version}
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`total` 就是列表长度** | 不是 COUNT 查询（与 `GET /version` 的 `total` 语义不同） |
| 2 | **`default` 分支把 `module` 回填成 `"gaop"`** | 不管入参传什么 module，响应里的 `module` 都是 `gaop`（实测①） |
| 3 | 内置语言包只支持**三种基础语言** | `zh_CN`/`en_US`/`fr_FR`；非基础语言返回**空列表**（厂商 `isNotBaseLanguage` 短路） |
| 4 | `version` 空白 ⇒ 取**当前版本** | 当前版本 = `g_language_version` 里 `used=1` 那行（实测③ 落到 `default` 分支） |

**资源布局差异（非行为差异）**：厂商读 `classpath:messages.properties` / `messages_<lang>.properties`；
我们的资源在 `classpath:i18n/` 下，所以端口实现里前缀是 `i18n/`。键值集合一致。

## 4. 实现与验证

**实现**：`ILanguageQueryService.queryLanguageDetail` + `LanguageController` 的 `GET /detail` +
`LanguageInfoDTO`/`LanguageEntryEntity`/`LanguageEntryPO`；新增端口
`ILanguagePropertiesPort`（读内置语言包，实现放 infrastructure 用 Spring `ClassPathResource`）；
`ILanguageVersionDao.selectLanguages`（含可选 module 过滤）。

### 4.1 离线：`LanguageDetailTest` 5 例，全量 **485** 条 0 失败

覆盖：`default` ⇒ 读内置 + module 强制 `gaop` + 不查库；`version` 为空白/null ⇒ 取当前版本；
DB 分支的 module 透传与 `abbr` 映射；`total` = 列表长度。

### 4.2 在线：真库 + 本地资源（seed 两行 `g_language`：`k1/module=gaop`、`k2/module=other`）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `?lang=zh_CN&version=default` | `200`；`total=925`、`module=gaop`、`langVersion=default`、首条 `id=null`；**与本地 `messages.properties` 逐键逐值对账：925/925 完全相同** |
| ② | `?lang=en_US&version=default` | `200`；`total=909`，首条值是真英文（走 `messages_en_US.properties`） |
| ③ | `?lang=zh_CN`（不带 version） | `200`；`total=925`、`langVersion=default`、`module=gaop` ⇒ 取到当前版本 |
| ④ | `?lang=xx_XX&version=v9&module=gaop` | `total=1`，`[k1]` |
| ⑤ | `?lang=xx_XX&version=v9`（无 module） | `total=2`，`[k1(gaop), k2(other)]` ⇒ 不过滤 |
| ⑥ | `?lang=xx_XX&version=v9&module=nope` | `total=0` |
| ⑦ | `?lang=xx-XX&version=v9` | `total=2` ⇒ `-`→`_` 归一化生效 |

> ① 是本轮最有说服力的证据：不是"能返回 925 条"，而是**拿返回的 925 个键值去和本地 properties 逐条比对，全部相同**——
> 一次同时验证了资源读取路径、映射规则与 `total` 语义。

### 4.3 收尾

seed 的 `g_language` 行已删（`g_language`=0、`g_language_version`=3 回到 lab 原值）、
临时 NodePort 与后端进程已回收。
