# `POST /v1/lang/version` 规格 + 实现（第 147 轮）

**证据等级**：〔代码事实〕（厂商 `LanguageController:73-80` + `LanguageServiceImpl.setLanguageVersion:421-437` /
`updateLanguageVersion:464-467` / `setDefaultVersionLang:469-483` / `exportLanguage2Properties:393-411` 逐行读）
+〔运行实测〕（真库 `used` 标记 + **真文件导出**）。2026-10-14。

## 1. 端点

```java
@PostMapping("/version")
public Response<Boolean> updateLanguageSpecialVersion(@RequestParam String lang, @RequestParam String version)
```

## 2. 流程

```
setLanguageVersion(lang, version)：
  if (lang == null || version == null) return false
  lang = lang2Underline(lang)                      // `-` → `_`
  if (version == "default"):
      updateLanguageVersion("default", lang)       // ① 全局 used=0 ② (lang,default) used=1
      return setDefaultVersionLang(lang)           // 非基础语言 ⇒ false；./i18n 存在 ⇒ 删目录后 true
  if (count(lang, version) == 0) return false
  return updateLanguageVersion(version, lang) > 0

updateLanguageVersion(version, lang)：
  **先** UPDATE g_language_version SET used = 0 WHERE used = 1     ← 全局清空
  **再** UPDATE g_language_version SET used = 1 WHERE lang=? AND lang_version=?
  返回第二步的受影响行数

控制器：ret = setLanguageVersion(...)
       if (ret) { exportLanguage2Properties(); kafka.sendLanguageChanged(当前语言版本) }
       return ok(ret)
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **"清 0"是全局的、且发生在任何校验之前** | 所以一次**失败**的调用也可能把表改成"没有任何 `used=1`"（见下） |
| 2 | **`default` 分支先删 `./i18n` 再导出** | `setDefaultVersionLang` 删目录 ⇒ 随后控制器又把**当前**语言包写成 `./i18n/messages_<lang>.properties`（实测③：目录里只剩新导出的那一个文件） |
| 3 | `default` 对**非基础语言**返回 false | 只认 `zh_CN`/`en_US`/`fr_FR`（实测④） |
| 4 | 非 default 版本**不存在 ⇒ false 且不写库** | `count == 0` 在 `updateLanguageVersion` **之前**判断（实测②） |

**厂商的一个真实缺陷（照抄）**：`POST /version?lang=de_DE&version=default` 会先执行全局 `used=0`，
然后 `setDefaultVersionLang` 因非基础语言返回 false ⇒ **整张表没有任何 `used=1` 行**（实测④）。
此时 `getCurrentLanguageWithVersion()` 会回落到 `g_setting` 里的语言（厂商）——我们的 `queryCurrent()`
把回落简化成常量 `zh_CN`/`default`（早前轮次的实现），**这条回落路径我们没做**，属于已知简化。

## 4. 实现与验证

**实现**：`ILanguageCommandService.setLanguageVersion` + `LanguageController` 的 `POST /version`；
DAO/仓储加 `resetUsed`/`setUsed`/`countByLangAndVersion`；新增端口
`ILanguageI18nDirectoryPort`（删 `./i18n`）与 `ILanguageChangedNotifyPort`（**空实现**：平台无 Kafka，只记日志）；
导出用 trigger 侧 `LanguagePropertiesWriter`（`Properties.store` 到 `./i18n/messages_<lang>.properties`）。

> **Kafka 差异（C 类）**：厂商发主题 `global_language`（失败只记日志）。我们的平台不含 Kafka ⇒ 只记一行日志，
> 主流程不受影响的语义保持一致。

### 4.1 离线：`LanguageSetVersionTest` 8 例，全量 **493** 条 0 失败

覆盖：null 入参 ⇒ false；正常版本"先 resetUsed 再 setUsed"且返回行数 > 0；版本不存在 ⇒ false 且不碰库；
`setUsed` 返回 0 ⇒ false；`default` 分支删目录/不删目录；**非基础语言 ⇒ false 但 used 标记已被改写**；
删目录失败 ⇒ 400 `globalization.file.delete.failed`。

### 4.2 在线：真库 + 真文件（seed `en_US/v2` 一行）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `?lang=en_US&version=v2` | `200 data:true`；**`en_US/v2` used=1、其余全 0**；导出 `i18n/messages_en_US.properties`（当前版本在库里没有词条 ⇒ 文件几乎为空，但确实生成） |
| ② | `?lang=xx_XX&version=v9` | `200 data:false`；库**无变化** |
| ③ | `?lang=zh_CN&version=default` | `200 data:true`；`zh_CN/default` used=1、其余 0；**目录里只剩 `messages_zh_CN.properties`（① 那个 en_US 文件被删掉了）且键数 = 925** ⇒ 第 3 节 2 实测确认 |
| ④ | `?lang=de_DE&version=default` | `200 data:false`，但**全部行 used=0** ⇒ 第 3 节的厂商缺陷实测确认 |

### 4.3 收尾

`used` 标记已还原（`zh_CN/default`=1、其余 0）、seed 的 `en_US/v2` 行已删、
**探针产生的 `./i18n` 目录已删**、临时 NodePort 与后端进程已回收。

## 5. 一次被"启动"抓住的自身缺陷（记录在案）

我用脚本给 `ILanguageVersionDao` 加方法时，把新方法的注解插到了 `selectLanguages` 的
`@Select("""...""")` 与它的方法声明**之间** ⇒ `resetUsed` 同时挂了 `@Select` 与 `@Update`。
**`mvn compile` 完全通过**（注解在语法上合法），但**应用启动时** MyBatis 抛
`BuilderException: Detected conflicting annotations ... on 'ILanguageVersionDao.resetUsed'`，上下文初始化失败。

教训：改 MyBatis 注解式 Mapper 后，**必须真的把应用启起来**（javac 抓不到这类错误）；
这也解释了为什么本项目"每轮都要在线探针"——它顺带充当了 mapper 的启动期校验。
