# `/v1/role` 关联写四端点（第 159 轮）

厂商出处：`RoleController.java:337-411`。四个端点一次做完：
`POST /v1/role/{roleId}/users`、`POST /v1/role/{roleId}/suRoles`、
`DELETE /v1/role/{roleId}/{userId}`、`DELETE /v1/role/{roleId}/app/{appId}`。

## 1. 规格（逐条照抄点）

| 端点 | 厂商调用链 | 关键行为 |
| --- | --- | --- |
| `POST /{roleId}/users` | `RoleController.usersRelateRole:339` → `UserService.usersRelateRole:767` | ① 战位不存在 ⇒ `role.storage.not.found`（**控制器**先查）；② 战位 key 是 `admin` 且存在用户 `admin` ⇒ **强制把 admin 用户塞进列表**；③ `HashSet` 去重后**先删光该战位全部用户关联再逐条插**；④ 每个 userId 先 `selectByPrimaryKey`，不存在跳过；⑤ 每插一条把**该用户的 `created_at` 覆盖成 now** |
| `POST /{roleId}/suRoles` | `RoleController.suRolesRelateRole:355` → `SubRoleServiceImpl.roleRelateSubRoles:401` | 同上，但 ⑤ 改成**把子角色的 `created_at` 覆盖成 now**；响应是 `Role`（不是 `RoleUsers`），只填 `subRoleList` |
| `DELETE /{roleId}/{userId}` | `RoleController.deleteUserInRole:403` → `UserService.deleteUserInRole:1368` | ① 战位不存在 ⇒ 400；② `user.username=="admin" && role.key=="admin"` ⇒ `user.cannot.remove.ofc.admin`；③ 该用户**在任意 `login-list:<user>:*` 里出现过**（= ON-LINE）⇒ 删掉值等于 `<user>:<roleKey>` 的 `gateway-token*` 键（只删第一个）与 `login-list:<user>:<roleKey>`；④ 删 `g_user_role` 单行；响应 `RoleUsers` |
| `DELETE /{roleId}/app/{appId}` | `RoleController.deleteRoleApp:158` → `RoleService.roleRemoveApp:732` | ① 战位不存在 ⇒ 400；② 删 `g_role_software` 单行；③ 推 `/topic/software` `{event:"DELETE", roleList:<roleId>}`；响应 `data:null` |

**响应形状**：`RoleUsers extends Role` + `List<User> list`（厂商 `controller/response/RoleUsers.java`）。
`BeanUtils.copyProperties(role, roleUsers)` **只搬标量** ⇒ `onLineUserList`/`softwareList`/`userList`/`subRoleList`
四个键**恒为 null**；`admin`/`dev`/`adminOrDev` 是 `Role` 上的计算 getter ⇒ 恒有值。
注意 `RoleUsers.list` 是**完整 `User`**，与 `Role.userList`（`{userId,userName}` 的 `RoleUser`）不是同一个东西。

## 2. 两个入口的差异（最容易做错的一处）

同一件事"战位关联子角色"有两个入口，厂商行为**不一样**：

| 入口 | 厂商方法 | 是否覆盖 `g_sub_role.created_at` |
| --- | --- | --- |
| `POST /v1/role`（新建战位） | `RoleService.subRolesRelateRole:610` | **不覆盖** |
| `POST /v1/role/{roleId}/suRoles` | `SubRoleServiceImpl.roleRelateSubRoles:401` | **覆盖成 now** |

所以仓储层有两个方法：`replaceSubRoleRelationsByRoleId`（不覆盖）与
`replaceSubRoleRelationsTouchingCreatedAt`（覆盖）。用户侧则**两个入口都覆盖**
（`RoleService.usersRelateRole` 与 `UserService.usersRelateRole` 是同一段代码）。

`created_at` 被覆盖是厂商**写错字段**（本意大概是 `updated_at`）。照抄，因为它可观测：
`/v1/sub_role/switchRole`、`/v1/role/switchRole` 的列表按 `created_at` 排序，覆盖会改变顺序。

## 3. 离线

`RoleRelationCommandTest` 8 例：admin 强制带 admin 用户、普通战位原样透传、战位不存在、
suRoles 走"覆盖"重载并返回 `subRoleList`、admin+admin 拒绝、普通用户先踢会话再删行、
未知用户先拒（不碰 Redis）、`removeAppFromRole` 只删单行。
全量 **588** 测试 0 失败（原 580 + 8）。

## 4. 在线（真集群 + 真 MySQL/Redis，探针后已还原）

| 例 | 实测 |
| --- | --- |
| `POST /v1/role/3/users` `["<admin-id>"]` | `200`；`data` = 战位 3 标量 + 四个集合键 **null** + `admin/dev/adminOrDev=false` + `list` 一条（`admin`，`password:null` 走既有 L3 账） |
| 同上 · 库侧 | `g_user_role` 新增 `(admin, 3)`；**admin 的 `created_at` 由 `1791931380848` 被覆盖成 `1791987365730`** ⇒ 照抄点实测确认；`(admin, admin 战位)` 那行**未被触碰** |
| `DELETE /v1/role/3/<admin-id>` | `200`；`list:[]`；`g_user_role` 该行消失 |
| `POST /v1/role/nope/users` | `400` `role.storage.not.found`（`{"msg":"席位不存在"}`） |
| `DELETE /v1/role/<admin-role-id>/<admin-id>` | `400` `user.cannot.remove.ofc.admin` |
| `POST /v1/role/3/suRoles` `["probe-sr-1"]` | `200`；`subRoleList` 一条；库侧 `g_sub_role.created_at` 由 `1000` 被覆盖成 `1791987387449` ⇒ 第二个照抄点确认 |
| `DELETE /v1/role/3/app/probe-app` + STOMP 订阅 | `200` `data:null`；`g_role_software` 行消失；**收到** `destination:/topic/software` 的 `{"event":"DELETE","roleList":"3"}` |

探针收尾：`g_sub_role`/`g_sub_role_role`/`g_role_software` 清空、`g_user_role` 还原成基线单行、
admin 的 `created_at` 还原、临时 `gaop-redis-tmp` NodePort 服务删除、后端进程停止。

## 5. 本轮新增偏离（详见 `docs/deviations.md`）

- **B25**：`DELETE /v1/role/{roleId}/{userId}` 的用户不存在 ⇒ 我们 **400 `user.id.not.found`**；
  厂商 `userMapper.selectByPrimaryKey(userId).get()` ⇒ `NoSuchElementException` ⇒ **500**。
- **B26**：同一端点上 `root` 用户 ⇒ 我们正常删关联；厂商 `getUserLoginStatusList` 把 `root`
  **过滤掉** ⇒ 返回空列表 ⇒ `userStatusInfos.get(0)` 抛 `IndexOutOfBoundsException` ⇒ **500**。

## 6. 覆盖率审计口径的第二处修正（`tools/coverage-audit.py`）

上一轮我把"连续 `/X` 折叠成一个"写进了归一化，这会把
`/v1/role/{roleId}/{userId}`（DELETE）和 `/v1/role/{id}`（GET）**合并成同一个键**，
于是前者的缺口被吃掉。本轮去掉该折叠后：

| 口径 | 修正前 | 修正后（含本轮 +4 端点） |
| --- | --- | --- |
| 前端调用面 | 240 | **241** |
| paas-core 已实现 | 109 | **113** |
| 剩余未实现 | 148 | **145** |

`/v1/role` 簇从 7 条降到 **3 条**：`POST /backup`、`POST /import`、`GET /import/template`
（都是 Excel 导入导出，下一轮做，可直接复用 `SubRoleExcelWriter`/`UserImportExcelParser` 基建）。

## 7. 未决 / 风险

- **`subRoleList` 的顺序**：厂商 `getSubRoleListByRoleId:385-397` 用 `HashSet` 去重后
  `new ArrayList<>(set)`，顺序由实体 `hashCode` 决定；我们按传入 id 顺序返回（既有偏离，见
  `findings/28`）。本环境 `g_sub_role` 为空 ⇒ **无法对拉**，本轮沿用同一处理。
- **`RoleUsers.list` 的顺序**：厂商该查询**没有 `ORDER BY`**，我们新增的
  `selectAllByRoleId` 同样不排序（**没有**沿用 `/v1/user/{roleId}/users` 那条
  `ORDER BY u.created_at DESC`——两条查询在厂商侧本来就是不同的语句）。
- `g_user_role` 上如果同一 `(user, role)` 有多行，`DELETE /{roleId}/{userId}` 会**一次删光**
  （厂商 `delete` 的 where 条件只有 roleId + userId，没有 limit）。照抄。
