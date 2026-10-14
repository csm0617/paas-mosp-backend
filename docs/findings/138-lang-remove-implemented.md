# `DELETE /v1/lang/remove` 规格 + 实现（第 145 轮）

**证据等级**：〔代码事实〕（厂商 `LanguageController:84-89` + `LanguageServiceImpl.deleteLangVersion:349-362` 逐行读）
+〔运行实测〕（真库，含词条级联与"缺参数"的全局行为）。2026-10-14。

## 1. 端点

```java
@DeleteMapping("/remove")
public Response<Boolean> deleteLangVersion(@RequestParam String lang, @RequestParam String version)
```
两个参数**都必填**。

## 2. 流程

```
finalLang = BaseLanguageEnum.lang2Underline(lang)          // `-` → `_`
if (version 非空 && "default".equals(version)) return false  // **不允许删默认版本**
versions = g_language_version where lang = finalLang and lang_version = version
if (空) return **true**                                    // 不存在也算成功（幂等）
lv = versions.get(0)
delete from g_language where lang = lv.lang and lang_version = lv.langVersion   // **先删词条**
delete = delete from g_language_version by primary key lv.id                    // 再删版本行
return delete > 0                                          // 返回"版本行是否真删掉"
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`default` 版本不允许删** | 直接 `false`，连查都不查（实测②） |
| 2 | **不存在 ⇒ `true`** | 幂等语义与 `deleteVersionById` 的返回不同（实测③） |
| 3 | **先删词条、再删版本行** | 两条表都要动；`g_language` 的 where 用的是**从版本行读出的** lang/lang_version（不是入参） |
| 4 | 返回值是 `delete > 0` | 删版本行返回 0 行时是 `false`（单测覆盖） |

> 环境事实：真实库的 `g_language` 比厂商 migration 里的 DDL **多两列**——`module`（`NOT NULL` 无默认值）
> 与 `abbr`/`description`。所以 seed 词条必须给 `module`，否则 `Field 'module' doesn't have a default value`。

## 4. 实现与验证

**实现**：**扩展现有的语言包基础设施**——`ILanguageVersionDao` 加
`selectByLangAndVersion`/`deleteVersionById`/`deleteLanguageEntries`（最后一条动 `g_language`），
`ILanguageVersionRepository` 加对应三方法；**新增** `ILanguageCommandService` + `LanguageCommandServiceImpl`
（写操作与已有的 `ILanguageQueryService` 分开）；`LanguageController` 加 `DELETE /remove`。

### 4.1 离线：`LanguageDeleteVersionTest` 5 例，全量 **480** 条 0 失败

覆盖：`default` 不删；`lang` 归一化后再查；不存在 ⇒ true；**先删词条再删版本行**的顺序；
返回值跟随 `deleteVersionById` 的行数。

### 4.2 在线：真库（seed 版本行 `xx_XX/v9` + 两条 `g_language` 词条）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `DELETE ?lang=xx-XX&version=v9` | `200 data:true`；**版本行 0 行、词条 0 行** ⇒ `-`→`_` 归一化 + 词条级联都生效 |
| ② | `DELETE ?lang=zh_CN&version=default` | `200 data:false`；`zh_CN` 行**仍在** ⇒ 默认版本受保护 |
| ③ | `DELETE ?lang=xx_XX&version=nope` | `200 data:true` ⇒ 幂等 |
| ④ | `DELETE ?lang=zh_CN`（**缺 version**） | **`500` + `{"msg":"系统错误","code":-1}`** |

**④ 的结论**：这不是缺陷，而是与厂商一致——厂商的 `@RestControllerAdvice` 里
`MissingServletRequestParameterException` 走 **`Throwable`** 分支（⇒ 500 + `code:-1`），
只有 `WrongArgumentException` 才是 400。我们的全局处理器行为与之相同。
（这也解释了为什么"参数缺失"不应返回 400——**只有业务校验失败才是 400**。）

### 4.3 收尾

seed 的版本行与词条已删（`g_language_version`=3 回到 lab 原值、`g_language`=0）、
临时 NodePort 与后端进程已回收。
