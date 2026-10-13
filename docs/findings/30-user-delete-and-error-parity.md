# 切片 3a 写路径：删用户（权限矩阵 + 假成功 + 级联）

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 四条路径与厂商**逐字节相同**（实测对拉）

| 场景 | 厂商 | 我们 |
| --- | --- | --- |
| 无 cookie | HTTP 401 / `code:401` / `msg:"未认证"` | 同 |
| 有 cookie 删 `admin` 用户 | HTTP 400 / `code:400` / `msg:"无法删除超级管理员admin用户"` | 同 |
| 有 cookie 删不存在的 id | HTTP 400 / `code:400` / `msg:"查无此id的用户"` | 同 |
| 批量 `/all_user?userId=nope` | HTTP 400 / `code:400` / `msg:"查无此id的用户"` | 同 |
| 成功删除 | `{"msg":"OK","code":0,"data":true}` | 同（实测，探针用户） |

## 2. 权限矩阵（照抄厂商 `UserController.getDeleteUser`，`UserController.java:320-358`）

| 调用者 | 目标 | 行为 |
| --- | --- | --- |
| username == `admin` | username == `admin` | **400** `role.can.not.delete.admin.key` |
| username == `admin` | 其他（含带 admin 战位者） | 删除 |
| 有 admin 战位（但用户名不是 admin） | 目标战位列表含 `admin` | **400** `role.cannot.delete.op.admin` |
| 有 admin 战位 | 其他 | 删除 |
| **既非 admin 用户、也无 admin 战位** | 任何 | **HTTP 200 + `data:true`，但什么都不做** |

最后一行是厂商的**假成功**：`UserController.java:357` 返回 null，控制器照样 `Response.ok(true)`。
**必须照抄**——改成 403 会让前端表现与厂商不同。已写进单测与 IT。

"有 admin 战位"的判定口径也要照抄：厂商用**该用户完整战位列表里是否有 `admin`**
（`RoleList.containAdminRole()`，`controller/response/RoleList.java:17`），不是"当前战位 key 是不是 admin"。

## 3. 两处只有实测才能发现的坑

### 3.1 不存在用户的文案不是 `user.not.found`

厂商控制器在删之前先调 `UserService.getById`，而 `getById` 抛的是 **`user.id.not.found`**（"查无此id的用户"，
`UserService.java:1365`）；`UserService.getDeleteUser` 里那句 `user.not.found`（"用户不存在"）
**在控制器路径上是死代码**。

**我第一次实现时按源码读成了 `user.not.found`，对拉时厂商返回"查无此id的用户"** → 已改正。
教训与 11/23 号证据同源：**源码里"看起来会抛"的那一句，不一定是实际路径上生效的那一句。**

### 3.2 405 必须走 `-1` 信封（在探测时顺手发现）

探测 `DELETE /v1/user`（漏了 id，路径变成集合）时发现：厂商 **500 + `code:-1` + "系统错误"**，
而我们给了 405 —— 因为我把所有 `ErrorResponse` 都还原成了 Spring 默认体。
修正为**只有 404 类还原 Spring 默认体**，详见 `docs/findings/24-error-contract.md` 第 3 节。
四条路径（404、405×3）修正后对拉全等。

## 4. 真实库级联验证

探针流程（已清理）：用**厂商**建一个用户 → 直接 SQL 插 `g_user_role`/`g_user_sub_role` 各一行 →
用**我们的后端**删 → 逐表核对：

```
删除前：g_user 1 / g_user_role 1 / g_user_sub_role 1
删除后：g_user 0 / g_user_role 0 / g_user_sub_role 0    用户总数回到 1（仅 admin）
```

## 5. 顺带发现的厂商缺陷（记录，**不复制**）

用**厂商** `POST /v1/user` 建用户时，请求体若**缺 `roleListKey`**，厂商在 `UserService.java:545`
`form.getRoleListKey().size()` 上抛 **NullPointerException** → HTTP 500 / `code:-1` / "系统错误"；
而**用户行在更早的 `:514` 已经插入**（`add` 没有事务）→ **留下一个半成品用户**。

实测证据：探针 `probeuser2` 就是这么被创建出来的（响应是 500，但库里多了一行）。

**决定**：重写版**不复制这个半成品**——下一轮实现 `POST /v1/user` 时，`roleListKey` 为 null 会按空列表处理
（厂商对 `userListKey`/`subRoleListId` 就是这么判的），并且**新增用户放在一个事务里**，避免留下半成品。
这是 ADR-0006 的 L3 精神：**厂商缺陷不自动成为契约**，要显式记录并说明。

## 6. 未实现 / 待办

- `POST /v1/user`（新增）、`PUT /v1/user/{userId}`（修改）、`POST /v1/user/userToRole`、`POST /v1/user/{userId}/roles`
  —— 下一轮；取证已齐（用户名/口令校验规则、`uuid` 额度分配、`rolesRelate2User` 的按类型替换语义）。
- `birth` 的序列化格式（`g_user.birth` 是 `date` 列，厂商模型是 `java.util.Date`）**未定论**：
  需要一次探针（建带生日的用户 → 读回）才能定；当前所有样本里 `birth` 都是 null。
