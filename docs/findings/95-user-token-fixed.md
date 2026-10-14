# B11 已修：`GET /v1/user` 的 `data.token` 置 null —— 契约比对 **L1+L2 全部通过**

**证据等级**：〔运行实测〕（对拉 + `tools/contract-check.py`）。2026-10-14。

## 1. 改动（一处，带证据注释）

`AuthServiceImpl.currentLogin`（**只被 `GET /v1/user` 使用**）里去掉 `.token(token)`：

```java
.role(role)
// **不设 token**：厂商 GET /v1/user（当前登录用户）走的是另一条构造路径，
// 实测 data.token 为 null（只有 login 会 setToken，见 UserService.loginedUser）。见 findings/94 的 B11。
.build()
```

厂商出处：`UserService.loginedUser(user, role, token)` 里有 `setToken(token)`，**但它只在 `login` 里被调用**；
当前用户那条路径不设 ⇒ `data.token = null`。

## 2. 验证（三件事，缺一不可）

| 检查 | 结果 |
| --- | --- |
| **登录响应仍带 token**（前端依赖，不能被误伤） | `login data.token = 'f5fda9f1-…'`，且 `Set-Cookie: gateway-token=f5fda9f1-…` ✅ |
| `GET /v1/user` 的 `data.token` | mine **None** / vendor **None** ⇒ **相等** ✅ |
| `tools/contract-check.py "GET /v1/user"` | **❌ 数 = 0**，结论「L1+L2 全部通过 ✅（门槛内等价，零偏离）」 ✅ |

**第二条最关键**：修这个差异时很容易顺手把 `token` 从**登录响应**里也去掉（因为它们共用 `LoginAssembler`）。
我特意先验证了"登录仍带 token、且 Set-Cookie 一致"，再确认 `/v1/user` 为 null——
**修一个差异时，先证明没有把相邻的正确行为改坏**。

## 3. 顺带确认：`/v1/user/info` 的失败仍是**样本陈旧**

重跑后那两条 `createdAt`/`updatedAt` 差异**依旧**（样本是更早时刻抓的，期间 `admin` 行被探针改过）
⇒ 再次确认它是工具性问题，不是回归。**处置待定**：给 `contract-check.py` 加"时间类字段排除"或在比对前重抓样本。

## 4. 状态

测试 **41 类 289 条全绿**；`docs/deviations.md` 的 B11 标记为**已修**；
临时 NodePort 已删、进程清零、工作树干净。
