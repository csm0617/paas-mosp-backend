Type: slice
Status: in progress (2026-10-14 开工)
Blocked by: 07（已落 ADR-0005）、08（已落 ADR-0006）、10（认证闭环已完成）

## 目标

按 [ADR-0005](../../docs/adr/0005-slice-order-and-cutover.md) 的顺序表，实现**序 3：用户 / 战位（K1 主体）**，并满足该 ADR 的域"完成"四条。

## 范围（我自己独立从 `02-contract-baseline.json` 提取，非转述）

K1 端点总数 **63**（`UserController` 25 + `RoleController` 28 + `SubRoleController` 10 + `RegisterController` 1）：
**前端有调用者 45**，**无调用者 18**。

**本切片只做 45 个"前端有调用者"的端点**——依据 ADR-0004：无调用者在门槛内可改，不属硬边界，留到最后（或永不做）。

### 3a 用户（`/usermanage/account`，`GET /v1/user` 的消费页）

读：`GET /v1/user/{roleId}/users`、`GET /v1/user/detailByUserId`、`GET /v1/user/info`、`GET /v1/user/tenx_user`、`GET /v1/user/get`、`GET /v1/user`
写：`POST /v1/user`、`PUT /v1/user/{userId}`、`DELETE /v1/user/{userId}`、`DELETE /v1/user/all_user`、`POST /v1/user/userToRole`、`POST /v1/user/login`、`POST /v1/user/logout`
批量：`POST /v1/user/import`、`GET /v1/user/import/template`、`POST /v1/user/backup`

### 3b 战位（`/usermanage/role`）

读：`GET /v1/role`、`GET /v1/role/list`、`GET /v1/role/{id}`、`GET /v1/role/switchRole`、`GET /v1/role/{roleId}/software`、`GET /v1/role/software`
写：`POST /v1/role`、`PUT /v1/role/{id}`、`DELETE /v1/role`、`POST /v1/role/{roleId}/users`、`DELETE /v1/role/{roleId}/{userId}`、`POST /v1/role/{roleId}/suRoles`、`POST /v1/role/{roleId}/software`、`PUT /v1/role/{roleId}/software`、`DELETE /v1/role/{roleId}/app/{appId}`
批量：`POST /v1/role/import`、`GET /v1/role/import/template`、`POST /v1/role/backup`

### 3c 子角色（`/usermanage/subrole`）

读：`GET /v1/sub_role/{id}`、`GET /v1/sub_role/switchRole`
写：`POST /v1/sub_role`、`PUT /v1/sub_role/update`、`DELETE /v1/sub_role/delete`、`POST /v1/sub_role/userAddRole/{userId}`、`POST /v1/sub_role/roleAddUser/{roleId}`
批量：`POST /v1/sub_role/import`、`GET /v1/sub_role/import/template`、`POST /v1/sub_role/export`

### 另记

`GET /register/ip/harbor` 归在 K1 的端点表里（`RegisterController` 唯一端点），但它探的是 Harbor，**属 K6 域**，本切片不做；它也是那个已知 404 的来源。

## 切片内的推进顺序（每个子切片都按此）

1. **读路径**——先让页面能渲染（e2e 门槛的最小集）；
2. **写路径**——CRUD 与不变量（充血聚合，规则从 controller 收回 domain）；
3. **批量**（import/export/backup）——最后，且必须先定 CSV 列序契约。

## 分层落点

| 层 | 落点 |
| --- | --- |
| Trigger | `http/UserController`、`http/RoleController`、`http/SubRoleController`（新）+ 各自 assembler |
| API | `api/dto/*`（请求/响应 DTO） |
| Case | 本切片先不引入独立 case 层，沿用"trigger → domain service"（与已完成的认证切片一致）；出现跨域编排（如删战位的 6 表级联）再补 |
| Domain | `domain/user`（扩展）、`domain/role`（扩展）、`domain/subrole`（新）；不变量落在实体/领域服务 |
| Infrastructure | `dao/I*Dao` + `mapper/*.xml`、`adapter/repository/*Repository`；只调 `dao/`、`redis/` |

## 不变量（必须进 domain，不能留在 controller——厂商就是漏在这里的）

- 内置战位 `admin`/`dev` 不可删、不可改 key；保留字 `admin`/`dev`/`cicd`/`root`；
- `admin` 用户不可删；非超管不可改超管；普通用户不可改他人；
- 删战位的**6 表级联删除必须原子**（`g_role`,`g_user_role`,`g_user_login`,`g_sub_role_role`,`g_role_software`,`g_user_audit`）；
- 用户名唯一（**库无唯一约束**，只能应用层判——并发下不成立，须显式建模）；
- 用户启停状态门禁；登录失败次数上限（Redis 计数 + TTL）；
- 错误键必须逐字复刻（`role.cannot.delete.storage`、`role.can.not.delete.admin.key`、`user.username.already.exists` …）。

## 验收（ADR-0005 的四条，逐条给证据）

1. 契约：45 个端点的 L1+L2 全绿；新偏离逐条入 `docs/findings/read-paths.json`；
2. e2e：厂商原样前端打开 `/gaop/usermanage/account` 与 `/gaop/usermanage/role`，表格有真实数据；
3. 回归：`/v1/service`、认证闭环、具名端口用例不退化（`mvn test` 全绿）；
4. 分层：无越层包。

## Answer

**3a（用户读路径）已完成并过门槛**（2026-10-14），证据 [docs/findings/26-e2e-user-page.md](../../docs/findings/26-e2e-user-page.md)：

- 契约：`GET /v1/user/info` 与厂商**在线响应逐字节相等**；L1+L2 已入 `read-paths.json`（12 条读路径 + 1 条已知偏离）；
- e2e：厂商原样前端 → `/gaop/usermanage/account` 渲染 10 列表头 + 1 行真实数据；A/B 与厂商**完全一致**；
- 回归：`mvn test` 32 → 35 条用例全绿（离线，不需集群）；
- 分层：domain/infra/trigger 各就位，无越层包。

**本轮附带完成的横切项**：厂商错误契约（400/401/500/-1 + 404 不套信封，见 `24-error-contract.md`）与 i18n 消息包；
`PageUtils` 照抄厂商 `SqlUtils`；更正 03 号证据的"无唯一约束"结论（见 `23-…`）。

**下一片**：3c 子角色（`GET /v1/sub_role/switchRole` 是 `/usermanage/account` 的**前置**，必须补），随后 3b 战位页。

## 3b 完成记录（2026-10-14）

已实现并过门槛，证据 [docs/findings/28-e2e-role-page.md](../../docs/findings/28-e2e-role-page.md)：

- `GET /v1/role`（补 page/size/username 过滤）、`GET /v1/role/list`（补 userList）、
  `GET /v1/role/switchRole`（新）、`GET /v1/role/{id}`（新，不存在时抛 `role.storage.not.found` → HTTP 400）；
- **5 个端点与厂商逐字节相等**；A/B 页面（标题/表头/数据行/导航）全一致；
- 新增端口 `IRoleOnlineUserRepository`（在线用户来自 Redis，不是库）；
- 测试 41 → **57 条**全绿（离线）。

**遗留**：`/usermanage/role` 还需要 `GET /v1/software`（K2 软件目录）才能用"关联应用"对话框——**属切片 1 的欠账**，
不是切片 3 的范围；已记入地图。下一片进入**写路径与不变量**（用户/战位 CRUD + 级联删除）。

## 3b 写路径第一项完成：删战位（2026-10-14）

`DELETE /v1/role?roleId=a,b,c` 已实现并过门槛，证据 [docs/findings/29-role-delete-cascade.md](../../docs/findings/29-role-delete-cascade.md)：

- 成功 + 两条失败路径**与厂商逐字节相同**（实测对拉）；
- **`paas-core-case` 启用为真正的一层**：事务边界必须覆盖整批（厂商 `@Transactional` 打在含 `forEach` 的方法上），
  故引入 `IRoleDeleteCase`/`RoleDeleteCaseImpl` + case 模块的 `spring-tx` 依赖；
- **真实库级联验证通过**（探针战位 + 5 张关系表，删完全为 0），H2 单测另有"整批回滚"用例；
- 测试 57 → **70 条**全绿。

**下一项**：用户写路径（`POST /v1/user`、`PUT /v1/user/{userId}`、`DELETE /v1/user/{userId}`、`POST /v1/user/{userId}/roles`）
与战位写路径（`POST /v1/role`、`PUT /v1/role/{id}`）——不变量：用户名唯一（DB 唯一索引 + 应用层双重）、
`admin` 用户不可删、非超管不可改超管、普通用户不可改他人。

## 3b 战位页（`/usermanage/role`）开工前的取证结论（2026-10-14，已备齐，直接可写）

数据源 = `GET /v1/role/switchRole?page&size&roleName&type`（`type` 1=战位 2=角色），**样本已抓**：
`runtime-samples/v1_role_switchRole_type1.json`（1 行 admin）与 `_type2.json`（1 行 user）。照抄厂商
`RoleService.switchRoleList`（`RoleService.java:191-261`）：

1. **两条分支**：关键字空 → `PageHelper` 分页、`total` 取分页总数；关键字非空 → `name LIKE`、`total = list.size()`；
   两条都按 `g_role.type = type` 过滤、按 `created_at` 倒序；
2. **`admin`/`dev` 的 `softwareList` 被硬编码覆盖**为单条伪条目（`id=0`、`appId=key`、`appName=运维管理员门户/开发管理员门户`），
   **即使 `g_role_software` 为空**；
3. **`onLineUserList` 来自 Redis**：`SCAN login-list*` 取键（形如 `login-list:<user>:<roleKey>`），
   按 `roleKey` 归组得到"该战位在线用户名"，无人在线时是 **`[]`**（不是 null）；
4. **`admin`/`dev`/`adminOrDev` 是模型上的计算 getter**（`Role.java:33-43`，由 `key` 推导），不是数据库列；
5. `userList` 条目形状 `{userId,userName}`；`subRoleList` 是子角色数组；
6. `nameFr`/`nameUs` 在 `switchRoleList` 里**恒为 null**（只有 `GET /v1/role/{id}` 才填 `nameUs`）；
7. `softwareList` 伪条目的 **12 个键**：`id,roleId,appId,appName,url,updatedAt,createdAt,order,software,icon,screenShotList,web`
   （其余为 null）。

**页面还需要的**：`GET /v1/role/{id}`（编辑抽屉）、`GET /v1/role/{roleId}/software`（**要会话**，无 cookie 是 401，
与 `/v1/role` 的匿名不同）、`GET /v1/role/{roleId}/users`。前两个的样本已在 `runtime-samples/`。

**读路径**（`p__role.61a39591.async.js`，已逐条核对，见 `25-k1-read-paths.md`）：
`list[].key/name/desc/updatedAt/type/nameFr/nameUs/softwareList/userList/subRoleList/onLineUserList`；
**`adminOrDev`/`admin`/`dev` 该页不读**（页面自己拿 `key` 比字面量）。

**端点计数口径更正**：本工单开工时按 03 号证据记 **63** 条（把 `AuthController` 7 条归给 K3）；
22 号档案按"controller 归属"记 **70** 条。**两个口径都不错，但对外引用时必须说明口径**——
本切片仍只做"前端有调用者"的 49 条里的 K1 部分。
