# `PUT /v1/software/update_icon` / `update_screenshot` 规格 + 实现（第 137 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:715-729` + `SoftwareService.updateIcon:2577-2595` /
`updateScreenshot:2556-2575` 逐行读）+〔运行实测〕（真集群 + 真库，含 `updated_at` 对比）。2026-10-14。

## 1. 两个端点

```java
@PutMapping("/update_icon")        // ?softwareId=   body SoftwareIcon{icon}
public Response<String> updateIcon(...)              // 返回传入的 icon（可能 null）

@PutMapping("/update_screenshot")  // ?softwareId=   body List<String>
public Response<List<String>> updateScreenshot(...)  // 返回传入的列表
```

## 2. 两个方法都是**三分支**（结构完全对称）

| 情形 | `updateIcon` | `updateScreenshot` |
| --- | --- | --- |
| 入参与库里**相同** | **直接 return，不写库**（`updated_at` 也不动） | 同（比较的是 `,` 连接后的串） |
| 入参非空且不同 | 写 `icon` + `updated_at = now` | 写 `screenshot`（**列表用 `,` 连接**）+ `updated_at = now` |
| 入参为 **null / 空列表** | **清空** `icon`（写 null）+ `updated_at = now` | **清空** `screenshot` |

软件不存在 ⇒ 400 **`software.not.found`**（注意**不是** `software.not.exist.by.id`——那是 `getById` 用的键）。

## 3. 一处主动收紧（`deviations.md` B21）

`updateScreenshot` 里厂商写的是 `if (!screenShotList.isEmpty())` ⇒ 请求体为 **null** 时 **NPE ⇒ 500**。
我们按"空列表"处理（即清空该列）。前端正常传数组，所以实际不可达。

## 4. 实现与验证

**实现**：`ISoftwareCommandService.updateIcon/updateScreenshot` + 控制器端点 + `SoftwareIconDTO`；
DAO/仓储新增 `updateSoftwareIcon` / `updateSoftwareScreenshot`（**只写该列 + `updated_at`**）。

> 为什么不复用现成的 `updateByPrimaryKey`：那条 SQL 刻意**不写 `updated_at`**（为对齐厂商的 `editSoftWare`），
> 而这两个方法要写；所以单开两条精确的 UPDATE。

### 4.1 离线：`SoftwareIconScreenshotTest` 8 例，全量 **437** 条 0 失败

覆盖：icon 的"相同不写库 / 不同写 / null 清空 / 软件不存在 ⇒ `software.not.found`"；
screenshot 的"逗号连接 / 相同不写库 / 空列表清空 / null 按空处理（B21）/ 软件不存在"。

### 4.2 在线：真集群 + 真库（seed 一行 `g_software{sw-icon}`，icon/screenshot 均为 NULL）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `{"icon":"AAA"}` | `200 data:"AAA"`；库里 `icon='AAA'`、`updated_at` 推进 |
| ② | 再传同一个 `{"icon":"AAA"}` | `200 data:"AAA"`；**`updated_at` 与 ① 完全相同** ⇒ "相同就不写库"实测确认 |
| ③ | `{"icon":null}` | `200 data:null`；库里 `icon=NULL` ⇒ 清空 |
| ④ | `["a.png","b.png"]` | `200 data:["a.png","b.png"]`；库里 `screenshot='a.png,b.png'` ⇒ 逗号连接 |
| ⑤ | `[]` | `200 data:[]`；库里 `screenshot=NULL` ⇒ 清空 |
| ⑥ | `softwareId=no-such` | `400 该软件不存在`（`software.not.found`） |

### 4.3 收尾

探针 `g_software` 行已删（`g_software`=0）、临时 NodePort 与后端进程已回收；实验室其余数据未动。
