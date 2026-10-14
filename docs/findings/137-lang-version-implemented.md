# `GET /v1/lang/version` 规格 + 实现（第 144 轮）

**证据等级**：〔代码事实〕（厂商 `LanguageController:59-64` + `LanguageServiceImpl.getLangVersionList:267-281` +
`SqlUtils.normalizePage/normalizeSize` + `BaseLanguageEnum.lang2Underline` 逐行读）
+〔运行实测〕（真库，含关键字归一化与 DESC 排序）。2026-10-14。

## 1. 端点

```java
@GetMapping("/version")
public Response<LanguageVersionInfo> getLangVersionList(
        @RequestParam(required=false) Integer page,
        @RequestParam(required=false) Integer size,
        @RequestParam(required=false) String keyword)
```
`LanguageVersionInfo{versionList: List<LanguageVersion>, total: long}`，
`LanguageVersion{id, lang, langVersion, used, createTime}`（**没有 `@JsonProperty`** ⇒ 键就是这些驼峰名）。

## 2. 流程

```
page = SqlUtils.normalizePage(page)      // null 或 <0 ⇒ 1；否则原值
size = SqlUtils.normalizeSize(size)      // null ⇒ 20；<=0 ⇒ 100；否则原值
if (keyword != null):
    keyword = keyword.replaceAll(" +", "")            // **去掉所有空格**
    keyword = BaseLanguageEnum.lang2Underline(keyword) // **`-` → `_`**
likeKeyword = (keyword 为空) ? null : "%" + keyword + "%"
分页查询 g_language_version：where lang LIKE likeKeyword，**order by create_time DESC**
total = **同一条 where 的 COUNT**（PageHelper 的 total）
```

## 3. 两处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **关键字先去空格、再把 `-` 换成 `_`** | 所以 `keyword=en-US` 会变成 `%en_US%`；`" en - US "` 也会命中 `en_US`（实测③④） |
| 2 | `page`/`size` 的归一化是**三个不同的默认值** | page：null 或 <0 ⇒ **1**；size：null ⇒ **20**、<=0 ⇒ **100**（`PageUtils` 已与厂商对齐，本轮直接复用） |

## 4. 实现与验证

**实现**：**扩展现有的语言包基础设施**（不是新建一套）——
`ILanguageVersionDao` 加 `selectLanguageVersionPage`/`countLanguageVersions`、
`ILanguageVersionRepository`/`LanguageVersionRepository` 加对应方法、
`ILanguageQueryService`/`LanguageQueryServiceImpl` 加 `queryLanguageVersionList`；
新增 `LanguageVersionEntity`/`LanguageVersionPageEntity`/`LanguageVersionPO`/`LanguageVersionInfoDTO`
与 `LanguageController`（目前只有这一个端点）。

### 4.1 离线：`LanguageVersionListTest` 5 例，全量 **475** 条 0 失败

覆盖：无关键字 ⇒ 不过滤；`" en - US "` ⇒ `%en_US%`；纯空格 ⇒ 不过滤；`page=3&size=20` ⇒ offset 40；
`total` 与列表用**同一条 where**。

### 4.2 在线：真库（lab 原有 3 行 + seed 两行 `xx_XX`，`create_time` 分别为 111/222）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 无参数 | `200`；`total=5`；顺序 `xx_XX(v2,222)`、`xx_XX(v1,111)`、`zh_CN`、`en_US`、`fr_FR` ⇒ **create_time DESC** |
| ② | `?keyword=zh` | `total=1`，`[zh_CN]` |
| ③ | `?keyword=en-US` | `total=1`，**`[en_US]`** ⇒ `-`→`_` 归一化生效 |
| ④ | `?keyword=" en - US "`（URL 编码空格） | `total=1`，`[en_US]` ⇒ 去空格 + 归一化都生效 |
| ⑤ | `?page=1&size=2` | `total=5`（全量计数）但**只返回 2 行** ⇒ 分页正确 |
| ⑥ | `?keyword=xx_XX` | `total=2`，**222 在前** ⇒ DESC |
| ⑦ | `?keyword=zzz` | `total=0`，`[]` |

### 4.3 收尾

seed 的 `xx_XX` 两行已删（`g_language_version`=3，回到 lab 原值）、临时 NodePort 与后端进程已回收。

## 5. 一次险些造成回归的失误（**记录在案**）

本轮开始时我**直接 `write` 新建了 `ILanguageQueryService` 与 `LanguageQueryServiceImpl`**，
而这两个文件**本来就存在**（第 100 多轮实现 `/v1/lang/current` 时建的，里面有 `queryCurrent()`）。
结果是把已有的 `queryCurrent()` 覆盖掉，编译立刻报 `找不到符号: 方法 queryCurrent()`。

处理：`git checkout --` 还原两个文件，改成**扩展**（把 `queryLanguageVersionList` 加进去），
并删掉我新建的并行 DAO/仓储（`ILanguageDao`/`ILanguageRepository`/`LanguageRepository`）——
语言包域**已经有** `ILanguageVersionDao`/`ILanguageVersionRepository`。

教训（已写入长期记忆）：**新建任何 domain 层文件前，先用 `git ls-files | grep -i <domain>` 看一眼是否已存在**；
`write` 会静默覆盖，而编译错误才是唯一的报警。
