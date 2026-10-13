# K1 读路径清单（切片 3a/3b）——前端到底读了哪些字段

**证据等级**：〔代码事实〕（前端产物静态检索）+〔运行实测〕（厂商响应样本）。
用途：`docs/findings/read-paths.json` 的 L2 判定依据（ADR-0006）。

## 方法

1. 从 `recovered-web/routes.json` 找到 K1 的三个页面与它们的 chunk：
   `/usermanage/account`（name `user`）、`/usermanage/role`（`role`）、`/usermanage/subrole`（`subRoleManage`）。
2. 在 `recovered-web/decompiled/` 里定位承载页面逻辑的 chunk：
   - 用户管理 = `p__user__user-setting.8925a570.async.js`（84 259 B）
   - 战位管理 = `p__role.61a39591.async.js`
   - 子角色管理 = `p__user__subRoleManage.8a7129a5.async.js`
3. 对每个字段名做字面量检索并**逐处看上下文**（只看出现次数会误判：`id` 有 128 次，绝大多数与用户行无关）。

## `GET /v1/user/info`（用户管理表的数据源）

实据（`p__user__user-setting.8925a570.async.js`）：

| 字段 | 证据（原文片段） | 判定 |
| --- | --- | --- |
| `data.list` | 表格 `dataSource` | 容器 |
| `data.total` | 分页 | 读 |
| `list[].id` | 行 key、编辑/删除目标 | 读 |
| `list[].username` | `"admin" !== U.username \|\| "admin" === e.username`（admin 行特判） | 读 |
| `list[].nickname` | `{ title: NICKNAME, width: 150, dataIndex: "nickname" }` | 读 |
| `list[].status` | 启停开关 `checked: "1" === n.status?.toString()`；`status: 1 === n.status ? 0 : 1` | 读 |
| `list[].updatedAt` | `dataIndex: "updatedAt"`，`render: t.updatedAt \|\| t.createdAt ? dayjs(...).format("YYYY-MM-DD HH:mm:ss") : "--"` | 读 |
| `list[].createdAt` | 同上，作 `updatedAt` 的兜底 | 读 |
| `list[].desc` | `{ title: DESCRIPTION_TEXT, dataIndex: "desc" }` | 读 |
| `list[].roles` | `{ title: RELATE_ROLE, dataIndex: "roles" }`；`!a.includes("admin")` | 读 |
| `list[].subRoles` | `{ title: RELATE_SUB_ROLES, dataIndex: "subRoles" }`；`0 === t.subRoles?.length && (i = "NONE_RELATE_ROLE")` | 读 |
| `list[].belongUsers` | `{ title: RELATE_ROLES, dataIndex: "belongUsers" }`；`t.belongUsers?.includes("运维管理员")` | 读 |
| `list[].lastLoginRole` | **该 chunk 出现 0 次** | **不读** |

**重要**：`belongUsers` 的**值语义**被前端当真了——它 `includes("运维管理员")`/`includes("开发管理员")`，
而厂商后端往这个字段里塞的是 `g_role.type=2` 的名字。也就是说**厂商的字段名错位是契约的一部分**：
如果重写版"纠正"成真正的用户列表，这一页的按钮显示逻辑会变。见 `23-k1-live-schema-and-unique-constraints.md`。

**同时被读、但不在本端点的**：`data.belongRole`（`UserDetailInfo`，来自 `GET /v1/user/detailByUserId`）
与 `N.roles.map(e => e.name)`（同上，那里的 `roles` 是**对象数组**）。⇒ 用户管理的"编辑抽屉"依赖
`/v1/user/detailByUserId`，它返回的 `roles`/`belongRole` 是 **Role 对象**而不是字符串——**同一个页面里
`roles` 有两种形状**（`/v1/user/info` 是字符串数组、`/v1/user/detailByUserId` 是对象数组），
不能用一个 DTO 通吃。

## `GET /v1/role/switchRole`（战位管理表的数据源）

实据（`p__role.61a39591.async.js`，102 652 B）——按 `dataIndex` 与字面量上下文核对：

| 字段 | 证据 | 判定 |
| --- | --- | --- |
| `data.list` / `data.total` | 表格 `dataSource` 与分页 | 容器 / 读 |
| `list[].key` | `{ dataIndex: "key" }`；`disabled: "admin" === n.key \|\| "dev" === n.key` | 读 |
| `list[].name` | `{ dataIndex: "name" }` | 读 |
| `list[].desc` | `{ dataIndex: "desc" }` | 读 |
| `list[].updatedAt` | `{ dataIndex: "updatedAt" }` | 读 |
| `list[].type` | 25 次出现，用于战位/角色分栏 | 读 |
| `list[].nameFr` / `nameUs` | 表单 `{ label: APPLICATION_NAME_FR, name: "nameFr" }` 与回填 `nameUs: a?.nameUs, nameFr: a?.nameFr` | 读 |
| `list[].softwareList` | `{ dataIndex: "softwareList" }` | 读 |
| `list[].userList` | `{ dataIndex: "username" }` + `userList` 4 次 | 读 |
| `list[].subRoleList` | `{ dataIndex: "subRoleList" }` | 读 |
| `list[].onLineUserList` | `{ dataIndex: "onLineUserList" }`（1 次） | 读 |
| `list[].adminOrDev` | **该 chunk 出现 0 次** | **不读** |
| `list[].admin` / `list[].dev` | 该 chunk 里 `"admin"`/`"dev"` 共 5 处，**全部是 `n.key === "admin"` 这类客户端比较**，不是读布尔字段 | **不读** |

**更正一处常见误判**：`adminOrDev`/`admin`/`dev` 这三个后端算好的布尔，**前端在战位页并不读**
（页面自己拿 `key` 比字面量）。厂商仍然返回它们，所以形状上要保留；但"因为前端要用才必须算"这个理由是**错的**——
真正需要它们的是别处（未逐条检索）。这类"看起来像给前端用的字段"必须以字面量检索为准。

## 待办（本清单随切片推进扩展）

- `GET /v1/user/detailByUserId`、`GET /v1/user/{roleId}/users`、`GET /v1/role/{id}` 的读路径尚未逐条检索。
- 子角色页（`p__user__subRoleManage`）尚未做同样处理。
