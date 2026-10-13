# 切片 3 收尾：`exist_role_name` + `GET /v1/user/{roleId}/users`，以及 **K2 `/v1/software` 的真实规模**

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14。

## 1. 本轮补齐的两个端点（都已对拉）

| 端点 | 实测 |
| --- | --- |
| `GET /v1/role/exist_role_name?roleName=` | 三个名字两侧**完全一致**（都是 `false`，见下） |
| `GET /v1/user/{roleId}/users?page&size` | 键集合/`total`/条目数/14 键条目**一致**；唯一差异是 `password` |

`exist_role_name` 三个名字都返回 `false` 的原因值得记一笔：库里战位名是**双重编码**的中文
（`docs/findings/11`），拿正常 UTF-8 的中文去查永远匹配不上 —— **厂商也一样**。也就是说这个端点
在本环境**无法返回 true**，不是我们实现得不对。要真正验证 `true` 分支，得用**库里实际存的字节**去查。

`GET /v1/user/{roleId}/users` 的两处照抄点：
- 响应键是 **`userList`**（不是 `list`；与 `/v1/user/info` 的 `list` 不同）；
- 厂商**列表层过滤掉 `root`，但 `total` 用含 root 的分页总数**——这个不一致照抄。

**一处刻意偏离**：厂商这个端点把 `password`（BCrypt 摘要）**原样返回**给前端；我们统一置 null
（与 `/v1/user`、`POST /v1/user` 同一条 L3 账）。

## 2. 一个必须记下来的过程教训：**批量改代码时不能只看脚本"打印成功"**

我这一轮用一个 Python 脚本一次性改 5 个文件（都是 `s.replace(...)` 后打印 "patched"），
其中 **`RoleController` 那条静默没生效**（锚点字符串因文件已被前一次改动而对不上），
但脚本照样打印了 "patched"，编译也照样通过（因为没有引用关系）。
结果：测试里 `/v1/role/exist_role_name` 被 `/{id}` 捕获 → 返回 400 "席位不存在"，
**而单元测试是"失败"才发现的，不是"没跑"发现的**。

**修正做法**：改完后**逐文件 grep 校验**（本轮做了），并对"新增映射"这类没有编译期引用的改动，
必须有**一条运行期断言**（测试或 curl）。批量替换脚本要么带断言、要么只做一处。

## 3. K2 `GET /v1/software` 的真实规模（地图上挂了很久的那笔账）

我去读了实现（`SoftwareController.java:142-150` → `SoftwareService.getSoftwareList:483-547`），
结论是：**它不是"只读列表"，而是一个带 K8s 富化的聚合端点**。每个软件行都要额外算四样东西：

| 字段 | 来源 |
| --- | --- |
| `releaseStatus` | `softWareReleaseStatus(key)`（按软件 key 推导发布态） |
| `web` | 0=没有 `g_service_port` 行；2=有行但找不到对应 Deployment；否则按 Deployment 现场算 |
| `url` | `CS` 类型拼 `gaop://cs?...`（含请求头里的 `x-forwarded-host`、远端账号口令）；其它类型用 `router_key` |
| `roleList` | `g_role_software` 反查 + `g_role` 取 key/name，并过 i18n |

再加上每个软件行的全部列（`g_software` 有 **22 列**）与 `SoftwareMessage` 的 4 个附加键，
以及分页/`name` 模糊（`software.search.length.invalid`，>36）/`type` 过滤/**两级排序**
（**`order_top` 恒降序在前**，再按 `order` 指定的字段）。

**为什么必须承认这一点**：我原本打算"先做一小片只读列表把席位页的关联应用对话框解开"，
但真相是——**一个只返回 id/name 的"简化版 /v1/software"会让前端拿到一份缺字段的数据**，
而厂商前端的软件列表页会直接用 `web`/`releaseStatus` 渲染状态列。宁可晚一轮做完整，也不做半份。

**下一轮的开工清单（已备齐）**：
1. `g_software` 的 22 列 + `SoftwareMessage` 的 4 个附加键（`releaseStatus`/`roleList`/`web`/`url`）；
2. `releaseStatus` 与 `web` 的推导规则需要**各建一个探针软件行**（本环境 `g_software` 为 **0 行**，
   现有样本 `v1_software.json` 是空列表 `{"softwareList":[],"total":0}`，只能锁住空分支）；
3. 对拉时注意：软件行有唯一约束（`key`/`name`），两侧探针要用**不同值**（上一轮的教训）。
