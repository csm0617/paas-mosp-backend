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

## 3. 认证路径**无法对拉**（如实记录）

| 请求 | vendor | mine |
| --- | --- | --- |
| 带有效 `gateway-token`（同一 token 在 `/v1/user/info` 两侧都 200） | **401 未认证** | 200（返回 `{list:[],total:0}`） |
| 不带 token | **401 未认证** | 400 `role.no.permission.view`（"没有权限查看"） |

厂商这个端点走 `userService.getCurrentUser(req)`，**同一个 token 在 `/v1/user/info` 能通过、在这里却 401**
⇒ 该端点的取用户路径与其它端点不同（或需要别的头部/机制）。**我们无法通过这条路径对拉认证分支**，
因此：管理员分支与拒绝分支**都以厂商代码路径为准实现**（`WrongArgumentException("role.no.permission.view")`），
差异出现在更早的认证层，不是业务规则差异。

**下一轮可查**：厂商 `getCurrentUser(req)` 到底从哪里取用户（header？session？拦截器写入的 attribute？）
——这决定了它是"厂商缺陷"还是"我们少传了一个头"。

## 4. 状态

测试 **41 类 285 条全绿**（上一轮 283）；服务切片仍 **15/33**（本条属 K1 角色域，不计入服务切片）。
临时 NodePort 已删、进程清零、工作树干净。
