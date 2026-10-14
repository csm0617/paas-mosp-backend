# `GET /v1/user/access_permission`：六组用例**对拉完全相等**（含厂商正则的"怪行为"）

**证据等级**：〔运行实测〕（Cookie 对拉）+〔代码事实〕。2026-10-14。

## 1. 实测（`path` 用 URL 编码传，两侧都带 Cookie）

| path | vendor | mine | 结果 |
| --- | --- | --- | --- |
| `/api/gaop/v1/user/info` | true | true | **相等** ✅ |
| `/` | true | true | **相等** ✅ |
| **`/whatever`** | **true** | **true** | **相等** ✅ |
| `/api/gaop/v1/role/software` | true | true | **相等** ✅ |
| `/v1/role/software` | true | true | **相等** ✅ |
| `/api/v2/something` | true | true | **相等** ✅ |

`/whatever` 也是 `true`，**不是我们写错**：厂商那三个正则里 `[tenx]*`/`[gaop]*`/`[v1]*` 都是**字符类**
（可匹配 0 次），所以 `res` 几乎能匹配任何短路径。**照抄了它，才得到一致结果**——
如果"顺手把正则改成 `(tenx|gaop)` 那种写法"，这六组用例会立刻不一致。

## 2. 无 Cookie 时：厂商 **500**，我们 400（B 类偏离，已记录）

```
vendor: 500  code=-1  msg=系统错误        ← NPE（判空写在 new 之后，永远不成立）
mine  : 400  code=400 msg=没有登录或登录过期，请重新登录   ← 厂商**本意**要抛的键
```
⇒ 我们实现其**本意**（`user.not.logged.in.or.expired`），并在 `docs/deviations.md` 记为 B 类偏离。

## 3. 实现结构（按 `findings/92` 的建议接线）

新建 **`IUserAccessService` + `UserAccessService`**（依赖 `IRoleRepository` + `ISoftwareRepository` + `IRoleQueryService`），
**不动 `UserQueryServiceImpl` 的构造**（那会牵连 4 处既有测试）。
用的 SQL 是上一轮就落地的 `selectServicePathsBySoftware`（`g_software_app ⋈ g_software ⋈ g_service_port`）。

## 4. 状态

端点映射 **61**；测试 **41 类 289 条全绿**（上一轮 285）。
临时 NodePort 已删、进程清零、工作树干净。
