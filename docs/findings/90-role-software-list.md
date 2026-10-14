# `GET /v1/role/{roleId}/software`：分页/排序口径已对，**但认证路径无法对拉**（服务/K1 边界）

**证据等级**：〔运行实测〕（对拉尝试）+〔代码事实〕+〔离线单测〕。2026-10-14。

## 1. 实现

厂商 `RoleController:110-119` + `RoleService.roleSoftwareList:416-418`：

```
管理员校验（非管理员 → 400 role.no.permission.view）
→ PageHelper.startPage(page,size)
→ select ... from g_role_software where role_id = ? **order by `order`**
→ 每行补 appName（i18n）与 url（CS 拼 gaop://cs?...；非 CS 用 routerKey + web 入口 + Deployment 现场状态）
```

本轮实现：**管理员校验 + 分页 + `order by order` + `{list,total}`**（`total` 来自独立 count，不受分页影响）。
**未实现**：`appName` 的 i18n 解析、`url` 的两种拼法（CS 的 `gaop://cs?...` 与非 CS 的 web 入口 + Deployment 状态）。

## 2. 顺带修掉一个**潜伏问题**：`g_role_software` 查询没有排序

原来 `selectSoftwareByRoleId` **没有 `ORDER BY`**，而厂商是 `order by \`order\``。
本环境 `g_role_software` 有 **0 行**，所以 `/v1/role`、`/v1/role/list`、`/v1/role/software` 三个已完成的端点
**从来没暴露过这个差异**——一旦库里有多行且顺序不同，它们就会与厂商不一致。
**已给该查询补上 `ORDER BY \`order\``**（同时也修了那三个端点）。

> 这是"**零行数据下的假通过**"——与本会话记过的"可解释的 0"是同一枚硬币的两面：
> 0 条数据只能证明"接口不报错"，不能证明"排序/过滤/字段映射正确"。

## 3. 认证路径：**上一版的结论是错的**——不是厂商的问题，是我把 token 发错了地方

> **⚠️ 第 84 轮更正**：下面那段"无法对拉"的结论**已被推翻**。真因很简单：
> 厂商 `UserService.getCurrentUser(req):883-903` 是从 **Cookie** 里取 `gateway-token` 的
> （`req.getCookies()` → 找名为 `gateway-token` 的 cookie），而我当时是用 **HTTP 头**发的 token。
> 换成 Cookie 之后，**厂商返回 200**，而且四组用例（两个战位、带 keyword、不存在的战位）**与我们的响应完全相等**：
>
> | 请求（两侧都用 Cookie） | 结果 |
> | --- | --- |
> | `/v1/role/1/software?page=1&size=20` | **完全相等** ✅ |
> | `/v1/role/2/software?page=1&size=20` | **完全相等** ✅ |
> | `/v1/role/1/software?...&keyword=x` | **完全相等** ✅ |
> | `/v1/role/999/software`（不存在的战位） | **完全相等**（`{list:[],total:0}`，**不是错误**）✅ |
>
> **方法教训**：请求 401 时，**先确认"token 应该放在哪"（Cookie 还是 Header）**，再对端点下任何结论。
> 这是本会话第二次"看起来是厂商坏了、其实是我的探针错了"（第一次是 `gaop` 空列表）。
>
> 顺带记一个事实：厂商同一应用里**两套取 token 的方式**并存——
> `/v1/user/info` 用 Cookie 和 Header **都能过**（实测 200/200），而 `getCurrentUser` 那条路径**只认 Cookie**。

## 3'. （已作废）认证路径"无法对拉"的记录

| 请求 | vendor | mine |
| --- | --- | --- |
| 带有效 `gateway-token`（同一 token 在 `/v1/user/info` 两侧都 200） | **401 未认证** | 200（返回 `{list:[],total:0}`） |
| 不带 token | **401 未认证** | 400 `role.no.permission.view`（"没有权限查看"） |

厂商这个端点走 `userService.getCurrentUser(req)`，**同一个 token 在 `/v1/user/info` 能通过、在这里却 401**
⇒ 该端点的取用户路径与其它端点不同（或需要别的头部/机制）。**我们无法通过这条路径对拉认证分支**，
因此：管理员分支与拒绝分支**都以厂商代码路径为准实现**（`WrongArgumentException("role.no.permission.view")`），
差异出现在更早的认证层，不是业务规则差异。

**已查清（第 84 轮）**：`getCurrentUser` 读的是 **Cookie**；我们用错位置了。见上面的更正。

## 4. 状态

测试 **41 类 285 条全绿**（上一轮 283）；服务切片仍 **15/33**（本条属 K1 角色域，不计入服务切片）。
临时 NodePort 已删、进程清零、工作树干净。
