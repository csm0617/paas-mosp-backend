# 22 · K1 身份与战位（Identity & Position）实现级证据档案

本文件是 **K1 身份与战位** 这一重写切片的实现级证据底稿：账号、战位（`g_role.type` 区分战位/角色）、子角色、
三张关系表，以及归属本上下文的登录/会话端点。目标读者是**照着它写代码的人**，因此每条断言都带出处。

## 目录

- [0. 证据分级与路径简写](#0-证据分级与路径简写)
- [1. 端点清单（70 条）](#1-端点清单70-条)
  - [1.1 方法学与数量自校验](#11-方法学与数量自校验)
  - [1.2 机器可校验表：method + path + frontendCalled](#12-机器可校验表method--path--frontendcalled)
  - [1.3 UserController（25 条）](#13-usercontroller25-条)
  - [1.4 RoleController（27 条）](#14-rolecontroller27-条)
  - [1.5 SubRoleController（10 条）](#15-subrolecontroller10-条)
  - [1.6 RegisterController（1 条）](#16-registercontroller1-条)
  - [1.7 AuthController（7 条）](#17-authcontroller7-条)
  - [1.8 汇总与「前端未调用」清单](#18-汇总与前端未调用清单)
- [2. 表结构（7 张）](#2-表结构7-张)
  - [2.0 ⚠️ 唯一约束事实核查（推翻既有结论）](#20-️-唯一约束事实核查推翻既有结论)
  - [2.1 g_user](#21-g_user)
  - [2.2 g_role](#22-g_role)
  - [2.3 g_user_role](#23-g_user_role)
  - [2.4 g_user_login](#24-g_user_login)
  - [2.5 g_sub_role](#25-g_sub_role)
  - [2.6 g_user_sub_role](#26-g_user_sub_role)
  - [2.7 g_sub_role_role](#27-g_sub_role_role)
  - [2.8 内置数据不是迁移建的](#28-内置数据不是迁移建的)
- [3. 不变量（逐条 file:line）](#3-不变量逐条-fileline)
- [4. 错误 / i18n key（线上契约）](#4-错误--i18n-key线上契约)
- [5. 认证边界](#5-认证边界)
- [6. paas-core 现状对照](#6-paas-core-现状对照)
- [7. 缺口与风险（排序）](#7-缺口与风险排序)
- [8. 未定论](#8-未定论)

---

## 0. 证据分级与路径简写

全文每条断言都带一个分级标记：

- 〔代码事实〕——从源码/迁移/前端产物**直接读到**（带 `file:line`）。
- 〔运行实测〕——在真实集群上观察到的响应/Redis 值（引用既有 findings 的实测记录）。
- 〔推断〕——由代码事实推导出的结论，**尚未被直接证据钉死**。

路径简写（与既有 findings 一致）：

| 简写 | 绝对路径 |
|---|---|
| `$BE` | `/Users/csm/Desktop/Project/msop/recovered/gaop-be/src/main/java/com/glriverside/gaop/be/` |
| `$MIG` | `/Users/csm/Desktop/Project/msop/artifacts/k8s/sql/db/migration/` |
| `$FE` | `/Users/csm/Desktop/Project/msop/recovered-web/` |
| `$CORE` | `/Users/csm/Desktop/PaaS/paas-core/` |

前端 service 层的「内部函数名 / 导出短名」记法沿用 02 号文档 §1.5：259 个构造函数里，
`fn X` 是模块 22239 内部的函数名，`exp YYY` 是在导出表 `n.d(t,{...})` 里的短名；
页面 chunk 通过 `.<短名>` 引用它。两者都可复现。

---

## 1. 端点清单（70 条）

### 1.1 方法学与数量自校验

5 个 controller 的类级前缀与端点总数（逐条 `@*Mapping` 注解原文核对）：

| Controller | 类级注解（原文） | 端点数 |
|---|---|---|
| `UserController` | `@RequestMapping(value={"/v1/user"})`〔源：`$BE/controller/UserController.java:88`〕 | 25 |
| `RoleController` | `@RequestMapping(value={"/v1/role"})`〔源：`$BE/controller/RoleController.java:57`〕 | 27 |
| `SubRoleController` | `@RequestMapping(value={"/v1/sub_role"})`〔源：`$BE/controller/SubRoleController.java:49`〕 | 10 |
| `RegisterController` | `@RequestMapping(value={"/register"})`〔源：`$BE/controller/RegisterController.java:17`〕 | 1 |
| `AuthController` | `@RequestMapping(value={"/auth"})`〔源：`$BE/controller/AuthController.java:83`〕 | 7 |
| **合计** | | **70** |

数量交叉校验：厂商运行态 openapi dump 在 `/v1/user`、`/v1/role`、`/v1/sub_role`、`/register`、`/auth`
五前缀下恰好 **70** 个 operation〔源：`$FE/regenerated/openapi-gaop-be.json` → `paths`，本文件脚本计数〕。
与 03 号文档 §K1 给的控制机端点数（25+27+10+1=63，**未计 `AuthController` 的 7 条**）口径不同 ——
03 号文档把 `AuthController` 归给 K3 授权域。本工单按任务要求把它纳入，故总数 70。**这不是矛盾，是口径差**。

---

### 1.2 机器可校验表：method + path + frontendCalled

`frontendCalled` 的判定规则（全部为静态证据，可复现）：

1. 在 `$FE/decompiled/umi.d08aa716.js` 的 webpack module `22239`（前端 service 层，259 函数）中重建
   `method + 路径模板`；这是前端**唯一**的统一请求入口。
2. 用 `n.d(t,{...})` 导出表把内部函数名映射到导出短名。
3. 在 `$FE/decompiled/**/*.js` 的全部 chunk 里检索 `.<导出短名>`；命中即认定有页面调用者。
4. 部分端点不走 service 层，而是页面 chunk 内联 axios（`/import`、`/export`、`/backup`、
   `/import/template`、`/update_avatar`），按 URL 字面量直接命中。

| method | path | frontendCalled |
|---|---|---|
| POST | `/auth` | false |
| POST | `/auth/admin/auth` | true |
| POST | `/auth/authing` | false |
| POST | `/auth/cancelAuth` | true |
| POST | `/auth/gene_identifier` | true |
| POST | `/auth/updateAuth` | false |
| POST | `/auth/updateAuthInfo` | true |
| GET | `/register/ip/harbor` | true |
| GET | `/v1/role` | true |
| POST | `/v1/role` | true |
| DELETE | `/v1/role` | true |
| POST | `/v1/role/backup` | true |
| GET | `/v1/role/exist_role_name` | false |
| POST | `/v1/role/export` | false |
| POST | `/v1/role/import` | true |
| GET | `/v1/role/import/template` | true |
| GET | `/v1/role/list` | true |
| POST | `/v1/role/recovery` | false |
| POST | `/v1/role/relate/app` | false |
| POST | `/v1/role/reset` | false |
| GET | `/v1/role/software` | true |
| GET | `/v1/role/switchRole` | true |
| PUT | `/v1/role/updateSubRoleRole` | false |
| GET | `/v1/role/{id}` | true |
| PUT | `/v1/role/{id}` | true |
| DELETE | `/v1/role/{roleId}/app/{appId}` | true |
| POST | `/v1/role/{roleId}/person` | false |
| GET | `/v1/role/{roleId}/software` | true |
| PUT | `/v1/role/{roleId}/software` | true |
| POST | `/v1/role/{roleId}/software` | true |
| POST | `/v1/role/{roleId}/suRoles` | true |
| GET | `/v1/role/{roleId}/subRoles` | false |
| POST | `/v1/role/{roleId}/users` | true |
| DELETE | `/v1/role/{roleId}/{subRoleId}/subRole` | false |
| DELETE | `/v1/role/{roleId}/{userId}` | true |
| POST | `/v1/sub_role` | true |
| DELETE | `/v1/sub_role/delete` | true |
| POST | `/v1/sub_role/export` | true |
| POST | `/v1/sub_role/import` | true |
| GET | `/v1/sub_role/import/template` | true |
| POST | `/v1/sub_role/roleAddUser/{roleId}` | true |
| GET | `/v1/sub_role/switchRole` | true |
| PUT | `/v1/sub_role/update` | true |
| POST | `/v1/sub_role/userAddRole/{userId}` | true |
| GET | `/v1/sub_role/{id}` | true |
| GET | `/v1/user` | true |
| POST | `/v1/user` | true |
| GET | `/v1/user/access_permission` | false |
| DELETE | `/v1/user/all_user` | true |
| POST | `/v1/user/backup` | true |
| GET | `/v1/user/detailByUserId` | true |
| POST | `/v1/user/export` | false |
| GET | `/v1/user/get` | true |
| POST | `/v1/user/import` | true |
| GET | `/v1/user/import/template` | true |
| GET | `/v1/user/info` | true |
| GET | `/v1/user/list` | false |
| POST | `/v1/user/login` | true |
| POST | `/v1/user/logout` | true |
| POST | `/v1/user/recovery` | false |
| POST | `/v1/user/redirect` | false |
| POST | `/v1/user/reset_user` | false |
| GET | `/v1/user/tenx_user` | false |
| POST | `/v1/user/update_avatar/{userId}` | true |
| POST | `/v1/user/userToRole` | true |
| GET | `/v1/user/{roleId}/users` | true |
| GET | `/v1/user/{userId}` | false |
| PUT | `/v1/user/{userId}` | true |
| DELETE | `/v1/user/{userId}` | true |
| POST | `/v1/user/{userId}/roles` | false |

**计数**：true = 49，false = 21，合计 70。21 条 false 的完整清单见 §1.8。

> 交叉校验：`$FE/api-catalogue.json` 只收录 **25** 条 K1 相关条目（URL 为纯字面量的子集），
> 另有 49 条 URL 用 `.concat(...)`/`+` 拼接，故 catalogue **不能**用作 frontendCalled 判据〔源：`$FE/api-catalogue.json`；
> 方法学同 02 号文档 §1.4〕。

---

### 1.3 UserController（25 条）

类级：`@Tag(name="用户") @RestController @RequestMapping(value={"/v1/user"})`〔源：`$BE/controller/UserController.java:86-88`〕。
统一信封由 `Response<T>` 定义：`{msg:String, code:int, data:T}`，成功 `msg="OK"`、`code=0`
〔源：`$BE/controller/response/Response.java:5-26`〕。

| # | 注解原文（file:line） | method path | 入参 | 返回 `data` 形状 | frontendCalled |
|---|---|---|---|---|---|
| 1 | `@GetMapping` `:132` | GET `/v1/user` | 无（读 cookie `gateway-token`） | `UserLogined extends User`：18 个 User 字段 + `token:String, role:Role, roles:List<Role>, subRoles:List<SubRole>, settingList:List<Setting>, style:String="default"` | ✅ `fn J` / `exp umG`；调用页 `layouts__index.37d36734.async.js`、`p__operate__home__index.a56e7a11.async.js` |
| 2 | `@GetMapping(value={"/list"})` `:157` | GET `/v1/user/list` | `page:Integer?`、`size:Integer?`、`keyword:String?` | `UserRoleList{list:List<UserRoles>, total:Long}` | ❌ |
| 3 | `@GetMapping(value={"/detailByUserId"})` `:171` | GET `/v1/user/detailByUserId` | `userId:String`（query，**required**） | `UserDetailInfo extends UserRoles` + `belongRole:List<Role>` | ✅ `fn Vt` / `exp IIg`；`p__user__user-setting.8925a570.async.js` |
| 4 | `@PostMapping(value={"/redirect"})` `:178` | POST `/v1/user/redirect` | body `OauthForm{code:String, role:String, redirectUri:String}` | `UserLogined` | ❌ |
| 5 | `@PostMapping(value={"/login"})` `:244` | POST `/v1/user/login` | body `LoginForm{username:@NotEmpty, password:@NotEmpty, role:@NotEmpty}`〔源：`$BE/controller/form/LoginForm.java`〕 | `UserLogined`（`password=null`，`token`=UUID） | ✅ `fn ie` / `exp x4y`；`p__user__login__index.ac680d6a.async.js` |
| 6 | `@PostMapping(value={"/logout"})` `:286` | POST `/v1/user/logout` | 读 cookie | `void`（**无信封**，HTTP 200 空体） | ✅ `fn ne` / `exp kSL`；`layouts__index...`、`p__PlatFormAuth...`、`p__operate__home__index...` |
| 7 | `@PostMapping` `:293` | POST `/v1/user` | body `UserForm{username, nickname, password, status:Integer=1, email, birth:Date, phone, desc, roleListKey:List<String>, userListKey:List<String>, subRoleListId:List<String>, avatar}`〔源：`$BE/controller/form/user/UserForm.java:11-31`〕 | `User` | ✅ `fn ee` / `exp cnR`；`p__user__user-setting...` |
| 8 | `@DeleteMapping(value={"/{userId}"})` `:310` | DELETE `/v1/user/{userId}` | path `userId:String` | `Boolean` | ✅ `fn a` / `exp h8f`；`p__user__user-setting...` |
| 9 | `@DeleteMapping(value={"/all_user"})` `:386` | DELETE `/v1/user/all_user` | `userId:String`（query，逗号分隔） | `Boolean` | ✅ `fn Si` / `exp Gcg`；`p__user__user-setting...` |
| 10 | `@GetMapping(value={"/{userId}"})` `:397` | GET `/v1/user/{userId}` | path `userId:String` | `User`（`password=null`，`detail()` 置空） | ❌ |
| 11 | `@PutMapping(value={"/{userId}"})` `:404` | PUT `/v1/user/{userId}` | path + body `UserForm` | `User` | ✅ `fn o` / `exp Nqq`；`p__user__user-setting...` |
| 12 | `@PostMapping(value={"/{userId}/roles"})` `:432` | POST `/v1/user/{userId}/roles` | path + body `List<String>` roleIdList | `UserRole`（恒为 `null`，`Response.ok(null)`） | ❌ |
| 13 | `@GetMapping(value={"/{roleId}/users"})` `:443` | GET `/v1/user/{roleId}/users` | path `roleId`、`page?`、`size?` | `UserList{userList:List<User>, total:Long}` | ✅ `fn Ft` / `exp Ul4`；`p__role.61a39591.async.js` |
| 14 | `@PostMapping(value={"/recovery"}, consumes={"multipart/form-data"})` `:452` | POST `/v1/user/recovery` | `file:MultipartFile`（`@RequestParam`） | `UserList`（恒 `null`；`handleRecoveryExcel` 是**空方法体**〔源：`$BE/service/UserService.java:947-948`〕） | ❌ |
| 15 | `@PostMapping(value={"/backup"})` `:463` | POST `/v1/user/backup` | body `List<String>` userIdList（`required=false`） | **不是 JSON**：`application/vnd.ms-excel` 二进制 + `Content-Disposition: attachment` | ✅ 内联 `(0,O.sY)("/v1/user/backup", ...)`；`p__user__user-setting...` |
| 16 | `@PostMapping(value={"/export"})` `:480` | POST `/v1/user/export` | 同 backup | 同 backup（同一实现 `generateExportExcel`） | ❌ |
| 17 | `@GetMapping(value={"/import/template"})` `:496` | GET `/v1/user/import/template` | 无 | xls 二进制 | ✅ 内联 `(0,O.oM)("/v1/user/import/template", ...)`；`p__user__user-setting...` |
| 18 | `@PostMapping(value={"/import"}, consumes={"multipart/form-data"})` `:510` | POST `/v1/user/import` | `file:MultipartFile`、`isCover:Boolean`（**均 `@RequestParam` 必填**） | `UserList`（恒 `null`） | ✅ 内联 `{url:"/v1/user/import", method:"POST"}`；`p__user__user-setting...`、`p__ServiceConfig...`、`layouts__index...` |
| 19 | `@PostMapping(value={"/reset_user"})` `:522` | POST `/v1/user/reset_user` | 无 | `Boolean` | ❌ |
| 20 | `@GetMapping(value={"/access_permission"})` `:529` | GET `/v1/user/access_permission` | `path:String`（query，必填） | `Boolean` | ❌ |
| 21 | `@PostMapping(value={"/update_avatar/{userId}"}, consumes={"multipart/form-data"})` `:537` | POST `/v1/user/update_avatar/{userId}` | path `userId`、`file:MultipartFile`（`@RequestPart`） | `User`（`detail(userId)`，`password=null`） | ✅ 内联 `{url:"/v1/user/update_avatar/".concat(A)}`；`layouts__index...`、`p__ServiceConfig...` |
| 22 | `@GetMapping(value={"/tenx_user"})` `:550` | GET `/v1/user/tenx_user` | `userName:String?`（query） | `List<TenxUserInfo>` | ❌ service 层有 `fn Zt`/`exp APM`，但**全量 chunk 无调用点** |
| 23 | `@GetMapping(value={"/get"})` `:557` | GET `/v1/user/get` | `page?`、`size?`、`keyword?` | `UserLoginList{list:List<LoginUserInfo{username,lastLoginRole,avatar}>, total:Long}` | ✅ `fn Bt` / `exp Ro5`；`p__user__login__index.ac680d6a.async.js` |
| 24 | `@GetMapping(value={"/info"})` `:572` | GET `/v1/user/info` | `page?`、`size?`、`keyword?` | `UserInfoResponse{list:List<UserInfoList>, total:long}`；`UserInfoList{id,nickname,username,status,updatedAt,createdAt,desc,lastLoginRole,roles:List<String>,subRoles:List<String>,belongUsers:List<String>}` | ✅ `fn Ut` / `exp aKp`；4 个页面 chunk |
| 25 | `@PostMapping(value={"/userToRole"})` `:588` | POST `/v1/user/userToRole` | body `RoleRelationInfo{userId:String, roleIdList:List<String>}` | `Boolean` | ✅ `fn te` / `exp NXI`；`p__user__user-setting...` |

**返回形状的钉死细节**

- `User`（模型）字段与类型〔源：`$BE/model/User.java:8-24`〕：
  `id:String, username:String, password:String, status:Integer, email:String, birth:java.util.Date,
  phone:String, updatedAt:Long, createdAt:Long, apiToken:String, tenxUsername:String,
  lastLoginRole:String, nickname:String, uuid:Integer, desc:String, avatar:String`。
- **`GET /v1/user` 会把 `g_user.password`（BCrypt 串）原样返回**：`BeanUtils.copyProperties(user, userLogined)`
  之后**没有** `setPassword(null)`〔源：`$BE/controller/UserController.java:147`〕。
  运行实测样本印证：`"password": "$2a$10$l1gMJx2Qyp4CnZQO5yFJ9.Lur16Bk.BIAtCL78RLh5uukrgN.UUCC"`〔源：`$CORE/docs/findings/runtime-samples/v1_user.json`〕〔运行实测〕。
  对比：登录（`loginedUser`）会 `setPassword(null)`〔源：`$BE/service/UserService.java:385`〕；
  `/v1/pub/user` 也会显式置空〔源：`$BE/controller/pub/PublicController.java:410`〕。
- `GET /v1/user` 还额外装配 `roles`（用户全部战位）、`subRoles`、`settingList`（`system.setting.flag`）、
  `style`（`StyleConfig.getTitle()`）〔源：`$BE/controller/UserController.java:148-152`〕；
  **登录响应这几项是 `null`**（`loginedUser` 不设置）〔源：`$BE/service/UserService.java:382-389`〕。
  → 二者「同形状不同填充」，是本上下文最容易踩的坑。
- `GET /v1/user/get` 的 `total` 是 **PageHelper 分页 total**（keyword 过滤后的总数）〔源：`$BE/service/UserService.java:1824`〕。
- `RoleUsers extends Role`，额外带 `list:List<User>`〔源：`$BE/controller/response/RoleUsers.java:11-14`〕。
- `RoleList{list:List<Role>, total:long}`、`SubRoleList{list:List<SubRole>, total:long}`、
  `RoleSoftwareList{list:List<RoleSoftware>, total:Long}`。

---

### 1.4 RoleController（27 条）

类级：`@Tag(name="战位") @RestController @RequestMapping(value={"/v1/role"})`〔源：`$BE/controller/RoleController.java:55-57`〕。

| # | 注解原文（file:line） | method path | 入参 | 返回 `data` | frontendCalled |
|---|---|---|---|---|---|
| 1 | `@GetMapping` `:69` | GET `/v1/role` | `page?`、`size?`、`username?` | `RoleList`（每项含 `softwareList`、`userList`；admin/dev 强制注入伪 `RoleSoftware{id:0,appId:"admin",appName:"运维管理员门户"}`） | ✅ `fn Ne` / `exp uJr`；`p__software...`、`p__user__login__index...` |
| 2 | `@GetMapping(value={"/switchRole"})` `:77` | GET `/v1/role/switchRole` | `page?`、`size?`、`roleName?`、`type:Integer`（**必填**，无 `required=false`） | `RoleList`（含 `onLineUserList`） | ✅ `fn an` / `exp KOh`；3 个页面 |
| 3 | `@GetMapping(value={"/list"})` `:88` | GET `/v1/role/list` | `page?`、`size?`、`roleName?` | `RoleList` | ✅ `fn un` / `exp UyX`；`p__user__user-setting...` |
| 4 | `@GetMapping(value={"/software"})` `:99` | GET `/v1/role/software` | 无（读会话取当前战位） | `RoleSoftwareList`；无战位时返回空对象 | ✅ `fn sn` / `exp Br3`；`layouts__index...`、`p__operate__home__index...` |
| 5 | `@GetMapping(value={"/{roleId}/software"})` `:110` | GET `/v1/role/{roleId}/software` | path `roleId`、`page?`、`size?` | `RoleSoftwareList` | ✅ `fn O` / `exp uOA`；`p__role...` |
| 6 | `@PutMapping(value={"/{roleId}/software"})` `:123` | PUT `/v1/role/{roleId}/software` | path + body `List<Long>` order | `RoleSoftwareList` | ✅ `fn R` / `exp LUV`；`p__role...` |
| 7 | `@PostMapping(value={"/{roleId}/software"})` `:135` | POST `/v1/role/{roleId}/software` | path + body `RelateSoftware{list:List<String>}` | `RoleSoftwareList` | ✅ `fn D` / `exp JDr`；`p__role...` |
| 8 | `@PostMapping(value={"/relate/app"})` `:150`（`@Deprecated`） | POST `/v1/role/relate/app` | body `RoleSoftwareForm{roleId, appId, appName, url}` | `RoleSoftware`（`null`） | ❌ |
| 9 | `@DeleteMapping(value={"/{roleId}/app/{appId}"})` `:158` | DELETE `/v1/role/{roleId}/app/{appId}` | path ×2 | `RoleSoftware`（`null`） | ✅ `fn Li` / `exp VA5`；`p__role...` |
| 10 | `@GetMapping(value={"/{id}"})` `:168` | GET `/v1/role/{id}` | path `id` | `Role`（带 i18n `nameUs`/`nameFr` 回退） | ✅ `fn x` / `exp Rp4`；`p__role...` |
| 11 | `@PostMapping` `:185` | POST `/v1/role` | body `RoleForm{key,name,nameFr,nameUs,desc,type:Integer,softwareList:List<String>,userList:List<String>,subRoleIdList:List<String>}` | `Role` | ✅ `fn Oe` / `exp _dE`；`p__role...` |
| 12 | `@GetMapping(value={"/exist_role_name"})` `:196` | GET `/v1/role/exist_role_name` | `roleName:String`（必填） | `Boolean` | ❌ |
| 13 | `@PutMapping(value={"/updateSubRoleRole"})` `:203` | PUT `/v1/role/updateSubRoleRole` | `roleId:String`（query）+ body `List<String>` | `Void`（`Response.ok()`） | ❌ |
| 14 | `@PutMapping(value={"/{id}"})` `:211` | PUT `/v1/role/{id}` | path + body `RoleForm` | `Role` | ✅ `fn P` / `exp Aqf`；`p__role...` |
| 15 | `@DeleteMapping(value={""})` `:255` | DELETE `/v1/role` | `roleId:String`（query，逗号分隔） | `Role`（`null`） | ✅ `fn M` / `exp Rdm`；`p__role...` |
| 16 | `@PostMapping(value={"/backup"})` `:265` | POST `/v1/role/backup` | body `List<String>` roleIdList（可空） | xls 二进制 | ✅ 内联 `(0,_.sY)("/v1/role/backup", ...)`；`p__role...` |
| 17 | `@PostMapping(value={"/export"})` `:281` | POST `/v1/role/export` | 同 backup | xls 二进制（同一 `generateExportExcel`） | ❌ |
| 18 | `@GetMapping(value={"/import/template"})` `:296` | GET `/v1/role/import/template` | 无 | xls 二进制 | ✅ 内联 `(0,_.oM)("/v1/role/import/template", ...)`；`p__role...` |
| 19 | `@PostMapping(value={"/recovery"}, consumes={"multipart/form-data"})` `:309` | POST `/v1/role/recovery` | `file:MultipartFile` | `List<Role>`（恒 `null`） | ❌ |
| 20 | `@PostMapping(value={"/import"}, consumes={"multipart/form-data"})` `:320` | POST `/v1/role/import` | `file:MultipartFile`、`isCover:Boolean` | `List<Role>`（恒 `null`） | ✅ 内联；`p__role...`、`p__ServiceConfig...`、`layouts__index...` |
| 21 | `@PostMapping(value={"/reset"})` `:331` | POST `/v1/role/reset` | 无 | `Boolean` | ❌ |
| 22 | `@PostMapping(value={"/{roleId}/users"})` `:339` | POST `/v1/role/{roleId}/users` | path + body `List<String>` 用户 id | `RoleUsers` | ✅ `fn Re` / `exp Mq9`；`p__role...` |
| 23 | `@PostMapping(value={"/{roleId}/suRoles"})` `:355` | POST `/v1/role/{roleId}/suRoles` | path + body `List<String>` 子角色 id | `Role`（`subRolesByRoleId`，带 `subRoleList`） | ✅ `fn Wi` / `exp PHz`；`p__role...` |
| 24 | `@GetMapping(value={"/{roleId}/subRoles"})` `:370` | GET `/v1/role/{roleId}/subRoles` | path、`page?`、`size?` | `SubRoleList` | ❌ |
| 25 | `@DeleteMapping(value={"/{roleId}/{subRoleId}/subRole"})` `:379` | DELETE `/v1/role/{roleId}/{subRoleId}/subRole` | path ×2 | `Role` | ❌ |
| 26 | `@PostMapping(value={"/{roleId}/person"})` `:391` | POST `/v1/role/{roleId}/person` | path + body `List<String>` | `RoleUsers` | ❌（前端 `/person` 命中项经核验来自 SheetJS 的 `xl/persons/person.xml`，非本端点〔源：`$FE/decompiled/8385.6103ad05.async.js`〕） |
| 27 | `@DeleteMapping(value={"/{roleId}/{userId}"})` `:403` | DELETE `/v1/role/{roleId}/{userId}` | path ×2 | `RoleUsers` | ✅ `fn ki` / `exp I2C`；`p__role...` |

> 注 1：`DELETE /v1/role` 的注解原文是 `@DeleteMapping(value={""})`，与类级 `/v1/role` 拼接为裸 `/v1/role`
> 〔源：`$BE/controller/RoleController.java:255`〕。
> 注 2：`POST /v1/role/{roleId}/software` 内部**回头调用** `GET /v1/role/{roleId}/software` 的实现，
> 因此它继承该端点的会话校验与 `role.no.permission.view` 抛错〔源：`$BE/controller/RoleController.java:144`〕。

**`Role` 模型字段**〔源：`$BE/model/Role.java:12-31`〕：
`id, key, name, nameFr, nameUs, updatedAt:Long, createdAt:Long, desc, type:Integer,
onLineUserList:List<String>, softwareList:List<RoleSoftware>, userList:List<RoleUser>, subRoleList:List<SubRole>`。
另有两个派生 getter：`isAdminOrDev()`、`admin`、`dev`、`isUser()`〔源：`$BE/model/Role.java:38-46`〕——
运行态样本里确实出现 `adminOrDev/admin/dev` 三个布尔键〔源：`$CORE/docs/findings/runtime-samples/v1_role.json`〕〔运行实测〕。

**`RoleSoftware` 模型字段**（决定 `softwareList` 形状）：`id:Long, roleId, appId, appName, url, updatedAt, createdAt, order:Integer, software, icon, screenShotList, web`〔源：`$FE/regenerated/openapi-gaop-be.json` schema；运行态样本键名一致〕。

---

### 1.5 SubRoleController（10 条）

类级：`@Tag(name="席位子角色") @RestController @RequestMapping(value={"/v1/sub_role"})`〔源：`$BE/controller/SubRoleController.java:47-49`〕。

| # | 注解原文（file:line） | method path | 入参 | 返回 `data` | frontendCalled |
|---|---|---|---|---|---|
| 1 | `@PostMapping` `:60` | POST `/v1/sub_role` | body `SubRoleUserForm{key,name,nameFr,nameUs,desc,userList:List<String>}` | `SubRole` | ✅ `fn Zi` / `exp JGG`；`p__user__subRoleManage.8a7129a5.async.js` |
| 2 | `@GetMapping(value={"/switchRole"})` `:73` | GET `/v1/sub_role/switchRole` | `page?`、`size?`、`subRoleName?` | `SubRoleList`（每项含 `userList`、`roleList`） | ✅ `fn Fi` / `exp uNt`；3 个页面 |
| 3 | `@PutMapping(value={"/update"})` `:85` | PUT `/v1/sub_role/update` | body `SubRoleUpdateReq{id,key,name,nameFr,nameUs,updatedAt,createdAt,desc,userList:List<String>}` | `Boolean` | ✅ `fn Vi` / `exp bIu`；`p__user__subRoleManage...` |
| 4 | `@DeleteMapping(value={"/delete"})` `:92` | DELETE `/v1/sub_role/delete` | body `SubRoleIdsForm{subRoleIdList:List<String>}` | `Boolean`；**空列表时返回 `Response.error(401,"The id can't be empty")`**〔源：`:95`〕 | ✅ `fn Ui` / `exp tPJ`；`p__user__subRoleManage...` |
| 5 | `@PostMapping(value={"/userAddRole/{userId}"})` `:101` | POST `/v1/sub_role/userAddRole/{userId}` | path `userId` + body `SubRoleIdsForm` | `Boolean` | ✅ `fn Hi` / `exp VYv`；`p__user__user-setting...` |
| 6 | `@PostMapping(value={"/roleAddUser/{roleId}"})` `:110` | POST `/v1/sub_role/roleAddUser/{roleId}` | path `roleId` + body `UserIdsForm{userIdList:List<String>}` | `Boolean` | ✅ `fn Gi` / `exp TFO`；`p__user__subRoleManage...` |
| 7 | `@PostMapping(value={"/export"})` `:120` | POST `/v1/sub_role/export` | body `List<String>` subroleIdList（可空） | xls 二进制 | ✅ 内联 `(0,Y.sY)("/v1/sub_role/export", ...)`；`p__user__subRoleManage...` |
| 8 | `@PostMapping(value={"/import"}, consumes={"multipart/form-data"})` `:136` | POST `/v1/sub_role/import` | `file:MultipartFile`、`isCover:Boolean` | `List<Role>`（恒 `null`，**注意泛型写成 Role**） | ✅ 内联；`p__user__subRoleManage...`、`p__ServiceConfig...`、`layouts__index...` |
| 9 | `@GetMapping(value={"/import/template"})` `:146` | GET `/v1/sub_role/import/template` | 无 | xls 二进制，**且显式设置 `Access-Control-Allow-Origin: *`**〔源：`:155-156`〕 | ✅ 内联 `(0,T.Sv)("/v1/sub_role/import/template")`；`p__user__subRoleManage...` |
| 10 | `@GetMapping(value={"/{id}"})` `:164` | GET `/v1/sub_role/{id}` | path `id` | `SubRole`（带 `nameUs`/`nameFr` 回退） | ✅ `fn Bi` / `exp RT0`（`url:"/v1/sub_role/"+e`）；`p__user__subRoleManage...` |

> ⚠️ 类内方法是**重载**：`:101` 与 `:110` 都叫 `userAddRole`，但参数类型不同（`SubRoleIdsForm` vs `UserIdsForm`）
> 〔源：`$BE/controller/SubRoleController.java:101,110`〕。改写时不要合并。

**`SubRole` 模型字段**〔源：`$BE/model/SubRole.java:11-23`〕：
`id, key, name, nameFr, nameUs, updatedAt, createdAt, desc, userList:List<SubRoleUser>, roleList:List<SubRoleRoleForm>`。
`SubRoleUser{id, username}`；`SubRoleRoleForm{id, key, name}`〔源：`$BE/service/impl/SubRoleServiceImpl.java:214,227`〕。

---

### 1.6 RegisterController（1 条）

类级：`@RestController @Tag(name="获取ip地址") @RequestMapping(value={"/register"})`〔源：`$BE/controller/RegisterController.java:15-17`〕。

| # | 注解原文 | method path | 入参 | 返回 | frontendCalled |
|---|---|---|---|---|---|
| 1 | `@GetMapping(value={"/ip/harbor"})`〔源：`:23`〕 | GET `/register/ip/harbor` | 无 | `String`（`HostConfig.getHarbor()`，形如 `http://<ip>:<port>`） | ✅ `fn ti` / `exp QL3`；`p__DeployMent__components__DeploymentViewer...`、`p__NetService__components__NetServiceViewer...`、`p__ServicePod...`、`5328.f3271109.async.js` |

---

### 1.7 AuthController（7 条）

类级：`@Tag(name="授权") @RequestMapping(value={"/auth"}) @RestController`〔源：`$BE/controller/AuthController.java:82-84`〕。

> **归域说明**：这个 controller 是**软件授权 / licence 域（K3）**，不是用户登录。它出现在本档案里
> 只因为用户要求「AuthController 全端点入清单」。本上下文（K1）**不实现**它。
> 其中的 `userService` 依赖是唯一与 K1 的接触点，且在看过的 7 个 handler 里**从未被调用**
> 〔源：`$BE/controller/AuthController.java:102`，全文无 `userService.` 调用点〕。

| # | 注解原文（file:line） | method path | 入参 | 返回 `data` | frontendCalled |
|---|---|---|---|---|---|
| 1 | `@PostMapping(value={"/gene_identifier"})` `:107` | POST `/auth/gene_identifier` | body `IdentifierForm{serviceIdList:List<String>}` | `String`（Base64 后的 identifier JSON） | ✅ `fn Rt` / `exp xCf`；`p__ServiceAuth.504b1a3e.async.js` |
| 2 | `@PostMapping(value={"/admin/auth"})` `:132` | POST `/auth/admin/auth` | `serviceIdList:String`（query，逗号分隔） | `List<AuthingResult>` | ✅ `fn xt` / `exp x77`；`p__ServiceAuth...`、`p__ServiceManage...` |
| 3 | `@PostMapping(value={"/authing"})` `:145` | POST `/auth/authing` | body `AuthingParameters{authToken,...}` | `List<AuthingResult>` | ❌（前端走的是 `/v1/pub/authing`：`fn Qe`〔源：`$FE/decompiled/umi.d08aa716.js`〕） |
| 4 | `@PostMapping(value={"/cancelAuth"})` `:260` | POST `/auth/cancelAuth` | body `List<String>` serviceIdList | `List<AuthingResult>` | ✅ `fn Dt` / `exp Pnc`；`p__ServiceAuth...` |
| 5 | `@PostMapping(value={"/updateAuth"})` `:267` | POST `/auth/updateAuth` | body `ServiceAuthForm` | `Boolean` | ❌（注释写「业务软件调用」〔源：`:266`〕） |
| 6 | `@PostMapping` `:274` | POST `/auth` | body `AuthForm{serviceId}` | `AuthData{machineCode,cert,serviceId,authType,authInterval}` | ❌ |
| 7 | `@PostMapping(value={"/updateAuthInfo"})` `:292` | POST `/auth/updateAuthInfo` | body `UpdateAuthParameters` | `Object`（`Response.ok()`，`data=null`） | ✅ `fn Ot` / `exp QSX`；`p__ServiceAuth...` |

---

### 1.8 汇总与「前端未调用」清单

| Controller | 端点数 | frontendCalled=true |
|---|---|---|
| `UserController` | 25 | 16 |
| `RoleController` | 27 | 18 |
| `SubRoleController` | 10 | 10 |
| `RegisterController` | 1 | 1 |
| `AuthController` | 7 | 4 |
| **合计** | **70** | **49** |

**21 条前端未调用（= 后端专属/死代码）**：

```
POST   /auth
POST   /auth/authing
POST   /auth/updateAuth
GET    /v1/role/exist_role_name
POST   /v1/role/export
POST   /v1/role/recovery
POST   /v1/role/relate/app
POST   /v1/role/reset
PUT    /v1/role/updateSubRoleRole
POST   /v1/role/{roleId}/person
GET    /v1/role/{roleId}/subRoles
DELETE /v1/role/{roleId}/{subRoleId}/subRole
GET    /v1/user/access_permission
POST   /v1/user/export
GET    /v1/user/list
POST   /v1/user/recovery
POST   /v1/user/redirect
POST   /v1/user/reset_user
GET    /v1/user/tenx_user
GET    /v1/user/{userId}
POST   /v1/user/{userId}/roles
```

〔代码事实〕其中 `POST /v1/user/recovery` 的实现体是**空方法**（`handleRecoveryExcel` 空实现），
说明它已经是死代码；`POST /v1/user/export` 与 `/backup` 是同一个 `generateExportExcel`，
前端只用 `/backup`。

**前端调用但厂商后端不存在**：

| path | 证据 | 判定 |
|---|---|---|
| `GET /v1/user/getLoginUser` | `$FE/decompiled/umi.d08aa716.js`：`"get http://".concat(window.location.hostname, "/zk-102-jsxx-software/zk-102-jsxx-back-8304/v1/user/getLoginUser")`；调用页 `p__operate__home__index.a56e7a11.async.js` | 〔代码事实〕厂商 70 条端点里**没有**它〔源：`$FE/regenerated/openapi-gaop-be.json`〕。它指向**另一个后端前缀**（`zk-102-jsxx-back-8304`），是遗留调试代码。重写**不应**实现它 |

**「只在我们已建模的页面才可达」的端点**：已建模/已实测打通的页面是 `/user/login`、`/operate/home`、
`/software/workloads`、`/software/application/service`〔源：`16/17/21` 号文档〕。
以下 K1 端点**只在未建模的 `/usermanage/*` 页面上可达**（因此目前不会被真实前端触发）：
`/v1/user/info`、`/v1/user/detailByUserId`、`/v1/user/userToRole`、`/v1/user/{roleId}/users`、
`/v1/role/suRoles`、`/v1/role/{roleId}/users`、`/v1/role/{roleId}/{userId}`、全部 `/v1/sub_role/*`、
以及各 import/export/backup。
路由对照〔源：`$FE/routes.json`：`/usermanage/account`（name=`user`）、`/usermanage/role`（`role`）、
`/usermanage/subrole`（`subRoleManage`）、`/usermanage/log`（`userLog`）〕。
→ **含义**：K1 的 49 个前端调用里，绝大多数不在已跑通的验收路径上；把它们全部实现之前，
现有 e2e 门槛**不会**覆盖到它们。

---

## 2. 表结构（7 张）

### 2.0 ⚠️ 唯一约束事实核查（推翻既有结论）

> **既有结论**（03 号文档 §4.0，行 421-431）：「全 87 个迁移里 `UNIQUE` 只出现 3 次」，
> 「其余 36 张表**没有任何唯一约束**：`g_software.key`、`g_role.key`、`g_user.username`……全部只靠应用层 `select` 后判断」。
>
> **本档案的核查结果：该结论对 K1 的 3 张表不成立（至少对 `g_user` / `g_role` 是错的）。**
>
> 根因是**大小写**：既有结论执行的是 `grep -rn 'UNIQUE'`（大写），而 V0.0.1 用的是**小写内联 `unique`**。
> 复现命令与结果：
>
> ```bash
> grep -rni 'unique' $MIG | wc -l    # → 9（不是 4）
> ```
>
> ```
> $MIG/V0.7.7__add_sub_role_tables.sql:9:  UNIQUE KEY `key` (`key`)
> $MIG/V0.0.1__init_table.sql:4:    username   varchar(36) unique comment '用户名',
> $MIG/V0.0.1__init_table.sql:18:    `key`      varchar(36) unique comment '战位标识',
> $MIG/V0.0.1__init_table.sql:60:    `key`      varchar(36) unique comment '资源标识',
> $MIG/V0.0.1__init_table.sql:62:    service_id varchar(128) unique comment '服务id',
> $MIG/V0.8.6__alter-language-unique.sql:3:ADD UNIQUE INDEX `uni_lang_version_module_key` ...
> $MIG/V0.4.8__add-oauth-code-table.sql:4:    code           varchar(255) unique,
> $MIG/V0.7.4__alter-language-and-init.sql:9:ADD UNIQUE INDEX `uni_lang_version_module_key`...
> $MIG/V0.7.4__alter-language-and-init.sql:17:ADD UNIQUE INDEX `uni_lan_version`(`lang`, `lang_version`) ...
> ```
>
> **没有**任何迁移 drop 掉这两个唯一索引（全量 `DROP` 扫描只命中 1 处语言索引 + 若干外键 + 一列
> 〔源：`$MIG/V0.8.6__alter-language-unique.sql:2`、`$MIG/V0.1.7__alter-sort-rules-table.sql:2-8`、
> `$MIG/V0.0.5__alter-user-table.sql:5`〕），`ALTER TABLE g_user CONVERT TO CHARACTER SET`
> 会**保留**索引〔源：`$MIG/V0.1.7__alter-sort-rules-table.sql:15`〕〔推断：MySQL `CONVERT TO` 语义〕。
>
> **对 K1 的后果**：
> 1. `g_user.username` 有 DB 级唯一索引 → 「用户名唯一」是**双重保障**（DB + 应用层 `existUsername`）。
>    03 号文档说「应用层 only」**是错的**。
> 2. `g_role.key` 有 DB 级唯一索引 → 「战位标识唯一」同样是**双重保障**。
> 3. `g_sub_role.key` 有 DB 级唯一索引 —— 这条既有结论**正确**。
> 4. 真正**无** DB 唯一约束的是：`g_user_role(user_id, role_id)`、`g_user_sub_role(user_id, sub_role_id)`、
>    `g_sub_role_role(sub_role_id, role_id)`、`g_user_login` 全表、`g_role.name`、`g_user.uuid`。
>    → 关系表的重复行在并发下**确实**会写进去。
>
> **推翻办法（若要在实时库上钉死）**：`SHOW INDEX FROM g_user;` / `SHOW INDEX FROM g_role;`
> 应看到 `Key_name = 'username'` / `'key'` 且 `Non_unique = 0`。本档案未连库，故只能到此〔未定论：见 §8〕。

「哪一列由哪个迁移引入」的完整推导见下表。

---

### 2.1 g_user

| 列 | 类型 | NULL | 默认 | 唯一/PK | 来源迁移:行 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | NOT NULL | — | **PK** | `V0.0.1:3` |
| `username` | `varchar(36)` | NULL（未显式 NOT NULL） | — | **UNIQUE** | `V0.0.1:4` |
| `password` | `varchar(100)` | NULL | — | — | `V0.0.1:5` |
| `status` | `tinyint(4)` | **NOT NULL** | — | — | `V0.0.5:2`（`after password`） |
| `email` | `varchar(100)` | NULL | — | — | `V0.0.1:6` |
| `birth` | `date` | NULL | — | — | `V0.0.1:7` |
| `phone` | `varchar(15)` | NULL | — | — | `V0.0.1:8` |
| `` `desc` `` | `text` | NULL | — | — | `V0.0.1:9` |
| ~~`token`~~ | ~~`varchar(64)`~~ | — | — | — | 建于 `V0.0.1:10`，**已被 `V0.0.5:5` `drop column` 删除** |
| `updated_at` | `bigint` | NULL | — | — | `V0.0.1:11` |
| `created_at` | `bigint` | NULL | — | — | `V0.0.1:12` |
| `avatar` | `mediumtext` | NULL | — | — | `V0.2.4:2`（首行无行号的巨型 base64 只是回填数据；DDL 是 `add avatar mediumtext null`） |
| `tenx_username` | `varchar(36) NOT NULL` → `NULL` → `varchar(128) utf8mb4_bin NULL` | NULL（终态） | NULL | — | `V0.2.9:2` → `V0.3.0:2` → `V0.8.2:4` |
| `api_token` | `varchar(256) NOT NULL` → `NULL` | NULL（终态） | NULL | — | `V0.2.9:5` → `V0.3.0:5` |
| `last_login_role` | `varchar(36)` → `varchar(128) utf8mb4_bin` | NULL | NULL | — | 见下方「文件名错配」 |
| `nickname` | `varchar(36)` → `varchar(128) utf8mb4_bin` | NULL | NULL | — | `V0.5.7:2` → `V0.8.2:6` |
| `uuid` | `int(10)` | NULL | — | — | `V0.6.0:2`（回填见 `V0.6.0:4-8`） |

- 表级字符集：`ALTER TABLE g_user CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin'`〔源：`$MIG/V0.1.7:15`〕。
- **文件名错配（重要）**：`last_login_role` 不在 `V0.2.9__alter-user-table.sql` 里，而是被塞在
  `V0.3.7__add-software-icon.sql:4-5`：
  `alter table g_user add last_login_role varchar(36) null comment '最后登录的战位的key';`
  〔源：`$MIG/V0.3.7__add-software-icon.sql:4-5`〕。按文件名找会找不到。
- 列顺序（终态）〔推断：由 `after` 子句推导，未连库核对〕：
  `id, username, password, status, email, birth, phone, desc, updated_at, created_at, avatar, api_token,
  tenx_username, last_login_role, nickname, uuid`。
  （`V0.2.9:2` 与 `:5` 都写 `after avatar`，二者先后取决于执行顺序；`V0.8.2:4-6` 再钉成
  `api_token → tenx_username → last_login_role → nickname`。列顺序不影响 JSON 契约。）

### 2.2 g_role

| 列 | 类型 | NULL | 唯一/PK | 来源迁移:行 |
|---|---|---|---|---|
| `id` | `varchar(36)` | NOT NULL | **PK** | `V0.0.1:17` |
| `` `key` `` | `varchar(36)` | NULL | **UNIQUE** | `V0.0.1:18` |
| `name` | `varchar(128)` | **NOT NULL** | — | `V0.0.4:2`（`after key`） |
| `` `desc` `` | `text` | NULL | — | `V0.0.1:19` |
| `updated_at` | `bigint` | NULL | — | `V0.0.1:20` |
| `created_at` | `bigint` | NULL | — | `V0.0.1:21` |
| `type` | `int(10)` | NULL | — | `V0.5.0:9`（注释「类型 1战位 2角色」） |

- **`g_role.name` 没有唯一约束**（`existKeyName` 只在应用层判重：`key OR name`）
  〔源：`$BE/service/RoleService.java:659-661`〕。
- `type` 的回填规则很反直觉〔源：`$MIG/V0.5.0:11-15`〕：
  `update g_role set type=2 WHERE key in ('admin','dev')`；`update g_role set type=1 WHERE key not in ('admin','dev')`。
  即 **`type=2` 是「角色」（admin/dev），`type=1` 是「战位」**，而 `RoleForm.type` 的 Schema 注释却写
  「类型 1战位 2角色」〔源：`$BE/controller/form/role/RoleForm.java`〕——**一致**。
  但 `switchRoleList` 的 `@Parameter` 描述写「类型1战位 2角色」〔源：`$BE/controller/RoleController.java:78`〕
  也与之一致。然而前端 `switchRole` 传的 `type` 语义需以实际页面为准〔未定论，见 §8〕。
- 表级字符集：`V0.1.7:22`。
- 名称/描述被 `V0.2.1` 改成「运维管理员战位」/「开发管理员战位」〔源：`$MIG/V0.2.1:1-8`〕。
  注意运行态样本里 `admin` 的 `name` 是「运维管理员门户」（已再次被改）〔源：`$CORE/docs/findings/runtime-samples/v1_role.json`〕〔运行实测〕。

### 2.3 g_user_role

| 列 | 类型 | NULL | PK/索引 | 来源 |
|---|---|---|---|---|
| `id` | `bigint` | NOT NULL | **PK AUTO_INCREMENT** | `V0.0.1:26` |
| `user_id` | `varchar(36)` | NULL | FK → `g_user(id)` | `V0.0.1:27,31` |
| `role_id` | `varchar(36)` | NULL | FK → `g_role(id)` | `V0.0.1:28,32` |
| `updated_at` | `bigint` | NULL | — | `V0.0.1:29` |
| `created_at` | `bigint` | NULL | — | `V0.0.1:30` |

- 外键名（终态）：`g_user_role_ibfk_1(user_id)`、`g_user_role_ibfk_2(role_id)`
  〔源：`$MIG/V0.1.7:30-31`；原名创建于 `V0.0.1:31-32`，`V0.1.7:2-3` 先 drop 再重建〕。
  MySQL 会为外键自动建同名索引〔推断〕。
- **无 `UNIQUE(user_id, role_id)`** → 重复关联行可写入。
- 表级字符集：`V0.1.7:16`。

### 2.4 g_user_login

| 列 | 类型 | NULL | PK/索引 | 来源 |
|---|---|---|---|---|
| `id` | `bigint` | NOT NULL | **PK AUTO_INCREMENT** | `V0.0.2:3` |
| `user_id` | `varchar(36)` | NULL | — | `V0.0.2:4` |
| `role_id` | `varchar(36)` | NULL | — | `V0.0.2:5` |
| `token` | `varchar(36)` | NULL | — | `V0.0.2:6` |
| `logout_at` | `bigint` | NULL | — | `V0.0.2:7` |
| `created_at` | `bigint` | NULL | — | `V0.0.2:8` |

- **无外键、无索引、无唯一约束**。表级字符集：`V0.1.7:27`。
- ⚠️ **这张表在厂商代码里从不写入**：全仓只有 `userLoginMapper.delete(...)`（`RoleService.java:723`、
  `:1158`），没有任何 `new UserLogin()` 或 insert；`UserLoginMapper` 的 insert 方法无处调用
  〔源：`grep -rn 'new UserLogin(' $BE` 只命中 `dao/UserLoginDynamicSqlSupport.java:11` 的静态字段〕〔代码事实〕。
  → 「6 表级联删除」里有 1 张**永远是空表**。重写时仍要保留 delete（契约），但**不需要**实现写入。

### 2.5 g_sub_role

| 列 | 类型 | NULL | PK/索引 | 来源 |
|---|---|---|---|---|
| `id` | `varchar(36) COLLATE utf8mb4_bin` | NOT NULL | **PK** | `V0.7.7:2` |
| `` `key` `` | `varchar(36) COLLATE utf8mb4_bin` | NULL | **`UNIQUE KEY `key` (`key`)`** | `V0.7.7:3,9` |
| `name` | `varchar(128) COLLATE utf8mb4_bin` | **NOT NULL** | — | `V0.7.7:4` |
| `` `desc` `` | `text COLLATE utf8mb4_bin` | NULL | — | `V0.7.7:5` |
| `updated_at` | `bigint(20)` | NULL | — | `V0.7.7:6` |
| `created_at` | `bigint(20)` | NULL | — | `V0.7.7:7` |

`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin`〔源：`$MIG/V0.7.7:10`〕。
**没有任何插入 seed 数据**（`V0.7.7` 只有 14 行，无 `INSERT`）〔代码事实〕。

### 2.6 g_user_sub_role

| 列 | 类型 | NULL | PK/索引 | 来源 |
|---|---|---|---|---|
| `id` | `varchar(36) COLLATE utf8mb4_bin` | NOT NULL | **PK** | `V0.7.8:2` |
| `user_id` | `varchar(36)` | NULL | — | `V0.7.8:3` |
| `sub_role_id` | `varchar(36)` | NULL | — | `V0.7.8:4` |
| `updated_at` | `bigint(20)` | NULL | — | `V0.7.8:5` |
| `created_at` | `bigint(20)` | NULL | — | `V0.7.8:6` |

**无外键、无索引、无唯一约束**〔源：`$MIG/V0.7.8:1-9` 全文〕。
注意主键是 `varchar(36)`（UUID），不是自增——与 `g_user_role` 不同。

### 2.7 g_sub_role_role

| 列 | 类型 | NULL | PK/索引 | 来源 |
|---|---|---|---|---|
| `id` | `bigint(20)` | NOT NULL | **PK AUTO_INCREMENT** | `V0.7.9:2` |
| `sub_role_id` | `varchar(36)` | NULL | `KEY g_sub_role_role_ibfk_1` + FK → `g_sub_role(id)` | `V0.7.9:3,8,10` |
| `role_id` | `varchar(36)` | NULL | `KEY g_sub_role_role_ibfk_2` + FK → `g_role(id)` | `V0.7.9:4,9,11` |
| `updated_at` | `bigint(20)` | NULL | — | `V0.7.9:5` |
| `created_at` | `bigint(20)` | NULL | — | `V0.7.9:6` |

`ENGINE=InnoDB AUTO_INCREMENT=9547`〔源：`$MIG/V0.7.9:12`〕——dump 时的自增水位，
说明实环境该表曾有 ~9500 行（种子数据未进迁移）。
**无 `UNIQUE(sub_role_id, role_id)`**。

### 2.8 内置数据不是迁移建的

> 〔代码事实〕**87 个迁移里没有任何一条激活的 `INSERT INTO g_user` / `INSERT INTO g_role`(admin/dev)**。
> - `V0.4.6__alter-init.sql` 用 `--` 注释掉了 admin/dev 战位、root 用户、root↔admin 关系
>   〔源：`$MIG/V0.4.6:1-16`〕；
> - 唯一的激活插入是 `V0.5.2:7` 的 `INSERT INTO g_role ... VALUES ('3','user','席位操作员',...,2)`
>   〔源：`$MIG/V0.5.2__add-init-auth-table.sql:7`〕；
> - `V0.2.1` 与 `V0.2.3` 只做 `update`，隐含假定 admin/dev 与各 setting 已存在
>   〔源：`$MIG/V0.2.1:1-8`、`$MIG/V0.2.3:1-9`〕。
>
> **后果**：全新库跑完 87 个迁移后，`g_role` 只有 `user` 一条，`g_user` 为空，
> **没有 admin 用户、没有 admin/dev 战位**。重写如果只依赖迁移，`POST /v1/user/login` 用 admin 会必然
> `role.storage.not.found.specified` 或 `user.name.or.passwd.incorrect`。必须**自带种子**（或从 live 库导）。

K1 依赖的 setting 初值（`V0.1.4` 建，`V0.2.3` 改终值）：

| key | 初值 | **终值** | 来源 |
|---|---|---|---|
| `system.permission.expired_time` | `12` | **`1440`**（分钟） | `V0.1.4:2` → `V0.2.3:1-3` |
| `system.permission.password_limit` | `5` | **`3`**（次） | `V0.1.4:4` → `V0.2.3:4-6` |
| `system.permission.retry_protection` | `30` | **`60`**（秒） | `V0.1.4:5` → `V0.2.3:7-9` |
| `system.setting.flag` | `10201` | — | `V0.1.4:7` |

**这修正了 12 号文档的「`userConfig.getMaxLogin()` 运行值未知」之外的另一个空白**：
登录失败阈值的**默认值**是 3 次 / 60 秒保护窗（12 号文档没写这两个值）。

---

## 3. 不变量

约定：**「控制器」= 规则写在 `controller/`**；**「服务」= 写在 `service/`**；**「DB」= 数据库约束**。
本项目关心这个区分，因为厂商把大量业务规则泄漏到了 controller 里。

| # | 业务规则 | 判定 | 执行位置 | 出处（file:line） |
|---|---|---|---|---|
| I1 | **内置战位 `admin`/`dev` 不可删** | 成立 | **服务** | `$BE/service/RoleService.java:716-719`：`if ("admin".equals(key) \|\| "dev".equals(key)) { log.debug("无法删除当前战位：{}", key); throw new WrongArgumentException("role.cannot.delete.storage", new Object[0]); }` |
| I2 | **内置战位标识不可改** | 成立，但只在**控制器** | 控制器 | `$BE/controller/RoleController.java:218-223`：`if (form.getKey()!=null && role.isAdmin()) throw "role.can.not.edit.admin.key"`；`... role.isDev() → "role.can.not.edit.dev.key"` |
| I3 | **保留字 `admin`/`dev`** 不可被改成 | 成立，**控制器** | 控制器 | `$BE/controller/RoleController.java:224-226`：`if ("admin".equals(form.getKey()) \|\| "dev".equals(form.getKey())) throw new WrongArgumentException("role.identifier.reserved.word", form.getKey())` |
| I4 | 保留字 **`cicd`/`root`** | **不成立（推翻）** | — | `RoleConstant.CICD_KEY="cicd"` / `ROOT_KEY="root"` 仅**声明**未被引用〔源：`$BE/constant/RoleConstant.java:7-10`；`grep -rn 'CICD_KEY\|ROOT_KEY' $BE` 除定义外零命中〕〔代码事实〕。`editRole` 只检查 `admin`/`dev`。→ 一个战位可以被改成 `root` 或 `cicd` |
| I5 | **`admin` 用户不可删** | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:377-379`：`if (user.getUsername().equals("admin")) throw new WrongArgumentException("role.can.not.delete.admin.key")` |
| I6 | **admin 不可被非超管编辑** | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:417-421`：`if (adminRole) { if (userId.equals(adminId)) throw new WrongArgumentException("role.cannot.modify.super.op.admin.storage"); }` |
| I7 | **普通用户不可编辑他人** | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:424-427`：`if (id.equals(userId)) {…} throw new SystemException("role.normal.user.cannot.modify.other.user")` |
| I8 | **只有 `admin` 用户名能新增用户** | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:297-299`：`if (!this.userService.isAdmin(currentUser.getUser())) throw new SystemException("role.normal.user.cannot.user")`；`isAdmin` 检查 `g_user_role` join `g_role` 里是否有 `key='admin'`〔源：`$BE/service/UserService.java:920-926`〕 |
| I9 | **普通管理员的删除范围**：不能删其它管理员，只能删席位操作员 | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:339-343`：`if (adminRole) { RoleList roleList = roleService.list(user.getUsername()); if (roleList.containAdminRole()) throw "role.cannot.delete.op.admin"; }`；非 admin 且非 adminRole → 返回 `null` → 控制器回 `Response.ok(true)`（**静默成功**）〔源：`:317,357`〕 |
| I10 | **登录失败次数上限 + TTL** | 成立，**服务 + Redis** | 服务 | `$BE/service/UserService.java:363-380`（`checkPasswordTimes`）与 `:246-280`（`increaseFailedLoginCounter`）。Redis key = `"login-error:" + username`（`:243,250,376`）。阈值读 `g_setting.system.permission.password_limit`（默认值 3，见 §2.8）；TTL 每次失败被 `expire(key, retryTime)` 重置为 `system.permission.retry_protection`（默认 60 秒）〔源：`:279`〕。超限抛 `user-login.error.exceeded.attempts(passwordTimes, ttl)` |
| I11 | **用户启用/禁用门禁** | 成立，**服务** | 服务 | `$BE/service/UserService.java:340-342`：`if (user.getStatus()==0) throw new SystemException("user.disabled")`。常量 `Status.UNAVAILABLE=0/AVAILABLE=1`〔源：`$BE/constant/UserConstant.java`〕 |
| I12 | **登录必须有关联战位** | 成立，**服务** | 服务 | `$BE/service/UserService.java:349-352`：`user_role` 无该 (user,role) 行 → `user.logging.absence.role(roleKey)`；战位 key 不存在 → `role.storage.not.found.specified(roleKey)`（`:345-347`） |
| I13 | **用户名唯一** | 成立，**DB + 控制器 + 服务** | 三者 | DB：`g_user.username ... unique`〔源：`$MIG/V0.0.1:4`〕；控制器：`$BE/controller/UserController.java:300-302` `if (userService.existUsername(username)) throw "user.username.already.exists"`；服务另有 `duplicateUsername(userId,userName)` 用于改名校验〔源：`$BE/service/UserService.java:579-587`〕 |
| I14 | **用户 uuid / 数据额度 256** | 成立，**服务** | 服务 | `$BE/service/UserService.java:484-501`：`uuidList.size() <= 256` 否则 `throw "user.data.limit.exceeded"`；uuid 取 0..254 中第一个空缺（循环上界 `i<255`，`:488`），初始 `-1` |
| I15 | **头像 ≤ 200 KiB** | 成立，**控制器** | 控制器 | `$BE/controller/UserController.java:539-542`：`long _200_KiB = 204800L; if (file.getSize() > _200_KiB) throw new WrongArgumentException("global.avatar.size.limit")` |
| I16 | **删除战位的 6 表级联删除事务** | 成立，**服务 + @Transactional** | 服务 | `$BE/service/RoleService.java:707` `@Transactional` + `:708-730`：对每个 id，按 `g_sub_role_role.role_id`、`g_user_audit.role_id`、`g_user_role.role_id`、`g_user_login.role_id`、`g_role_software.role_id` 删，再 `roleMapper.deleteByPrimaryKey`，再删 i18n。**注意**：`subRoleRoleMapper.delete` 用的 where 列常量写成 `RoleSoftwareDynamicSqlSupport.roleId`（`:720`）——是**跨表常量误用**，但 `role_id` 恰好同名同义，行为正确〔推断〕 |
| I17 | **删除用户的级联**（2 表） | 成立，**服务 + @Transactional（仅外层）** | 服务 | `$BE/service/UserService.java:815-818` `deleteRelateUserInfo` 删 `g_user_role` + `g_user_sub_role`；**`getDeleteUser`(`:829-834`) 自身无 `@Transactional`**，只有外层 `deleteUser`(`:820`) 有。而 `UserController.getDeleteUser` 直接调 `userService.getDeleteUser(userId)`〔源：`$BE/controller/UserController.java:380`〕→ **控制器路径上没有事务**〔代码事实〕 |
| I18 | **删除用户时禁止移除 admin↔admin 关联** | 成立，**服务** | 服务 | `$BE/service/UserService.java:1373-1375`：`if ("admin".equals(user.getUsername()) && "admin".equals(role.getKey())) throw new WrongArgumentException("user.cannot.remove.ofc.admin")` |
| I19 | **CSV/Excel 导入字段校验（战位）** | 成立，**服务** | 服务 | `$BE/service/RoleService.java:852-1041`：`:869-871` key 长度 1–36 → `role.key.is.illegal`；`:879-881` name 长度 1–100 → `role.name.is.illegal`；`:882-884` 空 key/name → `role.import.role.key.not.valid(i)`；`:908-910` desc>255 → `role.invalid.storage.description.length`；`:866-868 / :876-878` 单元格读取异常 → `role.key.is.null.or.format.error(e.getMessage())` / `role.name.is.null.or.file.format.error(...)` |
| I20 | **CSV/Excel 导入字段校验（用户）** | 成立，**服务** | 服务 | `$BE/service/UserService.java:1077-1079` username 长度 → `user.invalid.username.length`；`:1087` status 缺失 → `user.import.status.must.not.null(username)`；`:1090-1092` status ∉ `[01]` → `user.import.status.error(username)`；`:1102,1106` 密码空 → `user.password.empty(username)`；`:1124` 昵称缺失 → `user.nickname.empty(username)`；`:1135-1137` desc>255 → `user.invalid.user.description.length` |
| I21 | **CSV/Excel 导入字段校验（子角色）** | 成立，**服务** | 服务 | `$BE/service/impl/SubRoleServiceImpl.java:602-605` name → `subrole.name.is.null.or.file.format.error` / `subrole.name.is.illegal`；`:612-618` key → `subrole.key.is.null.or.file.format.error` / `subrole.key.is.illegal` / `subrole.import.key.not.valid(i)` |
| I22 | **导入文件类型必须是 Excel** | 成立，**控制器** | 控制器 | 4 处：`UserController.java:454-456,512-514`；`RoleController.java:311-313,322-324`；`SubRoleController.java:138-140`。判定 `ValidateUtils.isExcelFile`（Apache Tika MIME 白名单：`vnd.openxmlformats-officedocument.spreadsheetml.sheet`、`x-tika-ooxml`、`x-tika-msoffice`、`vnd.ms-excel`）〔源：`$BE/utils/ValidateUtils.java:18-23`〕，失败抛 `global.import.supported.file.type` |
| I23 | **导入覆盖时先删后插的"全量替换"语义** | 成立，**服务** | 服务 | `RoleService.importRoleExcel` 在 `existKeyName` 命中且 `isCover` 时先删 `g_user_role` + `g_role_software` 再更新〔源：`$BE/service/RoleService.java:885-893`〕；`UserService.importExcel` 的 `isCover` 走 `existUpdateUser`〔源：`$BE/service/UserService.java:1072-1075`〕 |
| I24 | **`g_sub_role_role` / `g_user_sub_role` 的 handle 是「先全删再插」** | 成立，**服务** | 服务 | `userAddRole` 先 `delete where user_id=userId`〔源：`$BE/service/impl/SubRoleServiceImpl.java:348-357`〕；`roleAddUser` 先 `delete where sub_role_id=roleId`（`:371`）；`roleRelateSubRoles` 先 `delete where role_id`〔源：`$BE/service/RoleService.java:618`〕 |
| I25 | **`admin` 战位删除用户时自动把自己加回** | 成立，**服务** | 服务 | `$BE/service/UserService.java:774-776`：`if ("admin".equals(role.get().getKey()) && (admin = getByUsername("admin")) != null) userIdList.add(admin.getId())`；`RoleService.usersRelateRole` 同款 `:640-642` |
| I26 | **列表隐藏 `root`** | 成立，**服务** | 服务 | `queryUsersByRoleId` 过滤 `!u.getUsername().equals("root")`〔源：`$BE/service/UserService.java:868`〕；`getUserList` `if (u.getUsername().equals("root")) continue`（`:1838`） |
| I27 | **导出/备份隐藏内置账号** | 成立，**服务** | 服务 | `generateBackupExcel` 跳过 `admin`/`dev`/`root`〔源：`$BE/service/UserService.java:956`〕；`generateExportExcel` 跳过 `admin`/`root`（`:984`）；`RoleService.generateExportExcel` 过滤 `admin`/`dev`/`user`（`:788-791`）；`generateBackUpExcel` 跳过 `admin`/`dev`（`:773`） |
| I28 | **`resetUser` 保留 admin/root，删其余（含审计）** | 成立，**服务** | 服务 | `$BE/service/UserService.java:1394-1407`：`deleteDefaultResetUser()` → 遍历用户，`admin`/`root` 跳过，删该用户全部 `g_user_audit` 行、`g_user_sub_role` 行、`g_user` 行。**无 `@Transactional`**〔代码事实〕 |
| I29 | **`resetRole` 保留 `admin`/`dev`/`user`** | 成立，**服务 + @Transactional** | 服务 | `$BE/service/RoleService.java:1150-1163`：`if ("admin".equals(key) \|\| "dev".equals(key) \|\| "user".equals(key)) continue;` 然后删 5 张关系表 + `g_role` |
| I30 | **战位管理软件列表需要管理员** | 成立，**控制器** | 控制器 | `$BE/controller/RoleController.java:114-117` 与 `:125-128`：`if (!userService.isAdmin(user.getUser())) throw "role.no.permission.view"` |
| I31 | **`GET /v1/role/software` 无战位时返回空对象** | 成立，**控制器** | 控制器 | `$BE/controller/RoleController.java:102-105`：`if (role == null) return Response.ok(new RoleSoftwareList())` → `{"list":null,"total":null}`（**不是** `{list:[],total:0}`）〔代码事实〕。运行样本是 `{list:[],total:0}`，因为实际有战位〔源：`runtime-samples/v1_role_software.json`〕〔运行实测〕 |
| I32 | **`switchRoleList` 的「在线用户」来自 Redis** | 成立，**服务** | 服务 | `$BE/service/RoleService.java:206-237`：`redisService.getLoginUser("login-list")` 按 `login-list:<user>:<roleKey>` 切出 `<roleKey> → 用户集合`，填 `onLineUserList` |
| I33 | **admin/dev 的软件列表被硬编码成伪门户** | 成立，**服务** | 服务 | `$BE/service/RoleService.java:238-256`（switchRoleList）、`:286-304`（roleList）、`:336-354`（listByRoleType）、`:376-394`（list）：`appId="admin"`/`"dev"`、`appName="运维管理员门户"`/`"开发管理员门户"`、`id=0` |

**结论（对重写的意义）**：K1 的**真不变量**集中在三处 ——
(a) 内置战位的删/改保护（I1–I4）、(b) 登录三件套（I10/I11/I12）、(c) 导入校验（I19–I22）。
其中 **I2/I3/I5/I6/I7/I8/I9/I15/I22 全部写在 controller 里**，只有 I1/I10/I11/I12/I14/I16–I21/I23–I33 在 service。
这就是「厂商把规则泄漏进 controller」在本上下文的具体形态：**9 条规则要从 controller 搬进领域层**。

---

## 4. 错误 / i18n key（线上契约）

### 4.1 错误响应形状

三类异常统一由 `ResponseExceptionConfig` 翻译〔源：`$BE/config/ResponseExceptionConfig.java:29-72`〕：

| 异常 | HTTP | `code` | `msg` | `data` |
|---|---|---|---|---|
| `UnauthorizedException` | **401** | `401` | i18n `user.unauthorized` | **`null`** |
| `SystemException` | **500** | `500` | i18n(key, args) | `ExceptionResponse{trace, message}`（**含 Java 堆栈**） |
| `WrongArgumentException` | **400** | `400` | i18n(key, args) | `ExceptionResponse{trace, message}` |
| 其它 `Throwable` | 500 | **`-1`** | `system.exception` | `ExceptionResponse{trace}` |

i18n 解析失败时**回退成 key 本身**〔源：`$BE/config/ResponseExceptionConfig.java:74-82`〕。
→ 这也是为什么 §4.2 里几个缺失 key 会**原样**出现在 `msg` 里。

> ⚠️ **改写必须复现的一点**：`WrongArgumentException`/`SystemException` 的 `data` 里带 `trace` 与 `message`，
> 且 `code` 是 HTTP 状态码（400/500），**不是** `-1`。这对前端是有意义的（前端在 401 时跳登录）。

### 4.2 K1 端点会抛的全部 key（含抛出点与实参）

下表 key 的**中文文案**取自 `$BE/src/main/resources/messages.properties`（默认 locale），英文取自
`messages_en_US.properties`。`{0}`/`{1}` 是 `MessageFormat` 位置参数。

| key | 抛出点（file:line） | 实参 | 中文文案 |
|---|---|---|---|
| `user-login.error.exceeded.attempts` | `UserService.java:276`（increaseFailedLoginCounter）、`UserService.java:378`（checkPasswordTimes） | `passwordTimes:int`, `ttl:Long` | 登录错误已超过 {0} 次，请 {1} 秒以后再次登录！ |
| `user.name.or.passwd.incorrect` | `UserService.java:327`（用户不存在）、`UserService.java:334`（口令错） | — | 用户名或密码不正确 |
| `user.passwd.bcrypt.abnormal` | `UserService.java:338` | — | 用户密码异常，请联系管理员重置密码（**EN 缺失**） |
| `user.disabled` | `UserService.java:341` | — | 用户已禁用 |
| `role.storage.not.found.specified` | `UserService.java:346`（登录时战位 key 不存在）；`RoleService.java:1111`（relateSoftware） | `roleKey` / `roleId` | 席位不存在 {0} |
| `user.logging.absence.role` | `UserService.java:351` | `roleKey` | 不在席位内: {0} |
| `role.normal.user.cannot.user` | `UserController.java:298` | — | 席位操作员无法新增用户 |
| `user.username.already.exists` | `UserController.java:301` | — | 用户名已存在 |
| `user.password.empty` | `UserController.java:304`；`UserService.java:1102,1106` | 无 / `username` | 密码不能为空 |
| `role.cannot.delete.op.admin` | `UserController.java:342` | — | 普通运维管理员不能删除其他运维管理员用户，只能删除席位操作员 |
| `role.can.not.delete.admin.key` | `UserController.java:378` | — | 无法删除超级管理员admin用户 |
| `role.cannot.modify.super.op.admin.storage` | `UserController.java:420` | — | 普通运维管理员席位无法修改admin超级运维管理员席位! |
| `role.normal.user.cannot.modify.other.user` | `UserController.java:427` | — | 席位操作员不能修改其他用户信息 |
| `user.not.logged.tip` | `UserController.java:408` | — | 请先登录! |
| `user.not.found` | `UserController.java:436`；`UserService.java:604,831` | — | 用户不存在 |
| `global.import.supported.file.type` | `UserController.java:455,513`；`RoleController.java:312,323`；`SubRoleController.java:139` | — | 只支持xls,xlsx文件的导入 |
| `global.avatar.size.limit` | `UserController.java:541` | — | 头像不能大于200KiB |
| `user-login.info.parsing.error` | `UserController.java:191`（OAuth 授权码换取 token 失败） | — | 登录失败，解析用户登录信息出现错误 |
| `user.parse.user.info.failed` | `UserController.java:199`（OAuth 用户不存在）；`SubRoleController.java:104`（userAddRole 用户不存在） | — | 解析用户信息失败，未找到该用户 |
| `role.no.permission.view` | `RoleController.java:116,127` | — | 没有权限查看 |
| `role.storage.not.found` | `RoleController.java:161,171,215,343,359,383,407` | — | 席位不存在 |
| `role.storage.existed` | `RoleController.java:189` | `key`, `name` | 席位的唯一标识{0}或席位的名称{1}已存在 |
| `role.can.not.edit.admin.key` | `RoleController.java:219` | — | 不能修改运维管理员的标识 |
| `role.can.not.edit.dev.key` | `RoleController.java:222` | — | 不能修改开发管理员的标识 |
| `role.identifier.reserved.word` | `RoleController.java:225` | `form.getKey()` | 标识 {0} 是保留字，不能修改为该标识 |
| `role.key.exist` | `RoleController.java:228` | — | 修改的席位标识已存在 |
| `role.change.name.exist` | `RoleController.java:231` | — | 修改的席位名称已存在 |
| `role.change.name.invalid` | `RoleController.java:234` | — | 修改席位名称不合法(长度范围在1~100个字符以内) |
| `role.change.key.name.invalid` | `RoleController.java:237` | — | 修改席位唯一标识不合法(长度范围在1~36个字符以内) |
| `role.change.desc.invalid` | `RoleController.java:243` | — | 修改描述不合法(长度范围在0~255个字符以内) |
| `user.username.not.empty` | `UserService.java:462` | — | 用户名不能为空 |
| `user.invalid.username.characters` | `UserService.java:465` | — | 用户名不合法，无法以空格或特殊字符字符作为用户名(用户名取值范围为A-Z,a-z,0-9,中文用户名) |
| `user.username.invalid.length` | `UserService.java:468` | — | 新增的用户名不合法(长度范围在2~36个字符以内) |
| `user.password.invalid.length` | `UserService.java:471` | — | 新增的用户密码不合法(长度范围在6~36个字符以内) |
| `user.document.link.invalid.length` | `UserService.java:474` | — | 新增的文电链接不合法(长度范围在0~36个字符以内) |
| `user.description.invalid.length` | `UserService.java:480` | — | 新增的用户描述不合法(长度范围在0~255个字符以内) |
| `user.data.limit.exceeded` | `UserService.java:499` | — | 用户数据已达到上限，最高为256位用户，请移除不需要的用户 |
| `database.error` | `UserService.java:516`（insert 返回 0） | — | 数据库错误 |
| `user.change.password.invalid` | `UserService.java:613` | — | 修改的密码不合法(长度范围在6~36个字符以内) |
| `user.change.username.exist` | `UserService.java:621` | — | 修改的用户名已存在 |
| `user.change.username.invalid` | `UserService.java:624` | — | 修改的用户名不合法(长度范围在2~36个字符以内) |
| `user.mail.invalid` | `UserService.java:628` | — | 修改的文电链接不合法(长度范围在0~36个字符以内) |
| `user.change.desc.invalid` | `UserService.java:634` | — | 修改的用户描述不合法(长度范围在0~255个字符以内) |
| `user.invalid.search.keyword.length` | `UserService.java:841`（list）、`:1833`（getUserList） | — | 搜索长度不合法，搜索关键字(用户名)的长度应在0~36个字符之内 |
| `user.invalid.search.keyword.length.range` | `UserService.java:1812`（getUserLoginList） | — | ……长度应在2~36个字符之内 |
| `user.invalid.username.length` | `UserService.java:1078`（导入） | — | （与 `user.username.invalid.length` 文案近似但 key 不同；**properties 里有此 key**） |
| `user.import.status.must.not.null` | `UserService.java:1087` | `username` | 导入的{0}用户状态不能为空 |
| `user.import.status.error` | `UserService.java:1091` | `username` | 导入的{0}用户状态填写有误(请填写0或者1) |
| `user.nickname.empty` | `UserService.java:1124` | `username` | 昵称不能为空 |
| `user.invalid.user.description.length` | `UserService.java:1136` | — | 导入的用户描述不合法,长度范围应在0~255个字符之内 |
| `user.cannot.remove.ofc.admin` | `UserService.java:1374` | — | 无法解除运维管理员的席位 |
| `user.not.logged.in.or.expired` | `UserService.java:1428`（accessPermission） | — | 没有登录或登录过期，请重新登录 |
| `user.missing.user` | `UserService.java:1753` | — | 找不到该用户 |
| `user.missing.user.info` | `UserService.java:1889`（userToRole） | — | 找不到改用户信息，无法进行关联操作 |
| `user.relate.role.not.exist` | `UserService.java:1950`（roleRelationUser） | — | 关联用户失败，找不到该角色，请检查该角色是否被删除 |
| `role.storage.not.found` | 见上 | — | 席位不存在 |
| `role.missing.storage` | `RoleService.java:713` | — | 找不到该席位 |
| `role.cannot.delete.storage` | `RoleService.java:718` | — | 无法删除该席位 |
| `role.empty.table.content.mismatch` | `RoleService.java:759`（recovery 空表） | — | 表格无内容，或内容不匹配 |
| `role.name.invalid.length` | `RoleService.java:538` | — | 新增席位名称不合法(长度范围在1~100个字符以内) |
| `role.unique.identifier.invalid.length` | `RoleService.java:541` | — | 新增席位唯一标识不合法(长度范围在0~36个字符以内) |
| `role.desc.invalid.length` | `RoleService.java:547` | — | 新增描述不合法(长度范围在0~255个字符以内) |
| `role.key.is.null.or.format.error` | `RoleService.java:867` | `e.getMessage()` | 导入的席位标识为空或导入文件格式错误:{0} |
| `role.key.is.illegal` | `RoleService.java:870` | `roleKey` | 导入的席位标识 {0} 不合法,长度应在1-36字符以内 |
| `role.name.is.null.or.file.format.error` | `RoleService.java:877` | `e.getMessage()` | 导入的席位名为空或导入文件格式错误: {0} |
| `role.name.is.illegal` | `RoleService.java:880` | `roleName` | 导入席位名称 {0} 不合法,长度应在1-100个字符以内 |
| `role.import.role.key.not.valid` | `RoleService.java:883` | `i:int`（行号） | row: {0} ，请填写合法的席位标识和席位名称 |
| `role.invalid.storage.description.length` | `RoleService.java:909,995` | — | 导入席位描述不合法,描述长度应在0-255个字符以内 |
| `sub.role.storage.existed` | `SubRoleController.java:64` | `key`, `name` | 子角色的唯一标识{0}或名称{1}已存在 |
| `subRole.parse.subRole.info.failed` | `SubRoleController.java:113` | — | **properties 里没有此 key** → `msg` 回退成 key 本身 |
| `sub.role.name.invalid.length` | `SubRoleServiceImpl.java:119` | — | 新增子角色名称不合法(长度范围在1~100个字符以内) |
| `sub.role.unique.identifier.invalid.length` | `SubRoleServiceImpl.java:122` | — | 新增子角色唯一标识不合法(长度范围在0~36个字符以内) |
| `sub.role.desc.invalid.length` | `SubRoleServiceImpl.java:128` | — | 新增描述不合法(长度范围在0~255个字符以内) |
| `sub.role.storage.not.found` | `SubRoleServiceImpl.java:236` | — | 子角色不存在 |
| `sub.role.key.exist` | `SubRoleServiceImpl.java:239` | — | 子角色key 已存在 |
| `sub.role.change.name.exist` | `SubRoleServiceImpl.java:242` | — | 修改的子角色名称已存在 |
| `sub.role.change.name.invalid` | `SubRoleServiceImpl.java:245` | — | 修改子角色名称不合法(长度范围在1~100个字符以内) |
| `sub.role.change.key.name.invalid` | `SubRoleServiceImpl.java:248` | — | 修改子角色唯一标识不合法(长度范围在1~36个字符以内) |
| `sub.role.change.desc.invalid` | `SubRoleServiceImpl.java:254` | — | 修改描述不合法(长度范围在0~255个字符以内) |
| `subrole.name.is.null.or.file.format.error` | `SubRoleServiceImpl.java:602` | `e.getMessage()` | 导入的子角色名为空或导入文件格式错误: {0} |
| `subrole.name.is.illegal` | `SubRoleServiceImpl.java:605` | `subroleName` | 导入子角色名 {0} 不合法,长度应在1-128个字符以内 |
| `subrole.key.is.null.or.file.format.error` | `SubRoleServiceImpl.java:612` | `e.getMessage()` | **properties 里没有此 key** → 回退成 key |
| `subrole.key.is.illegal` | `SubRoleServiceImpl.java:615` | `subroleKey` | 导入的子角色唯一标识 {0} 不合法,长度应在1-36字符以内 |
| `subrole.import.key.not.valid` | `SubRoleServiceImpl.java:618,635` | `i:int` | row: {0} ，请填写合法的唯一标识和子角色名 |

**缺失 key（必须原样复现"回退成 key"的行为）**：`user.passwd.bcrypt.abnormal`（EN 缺）、
`subrole.key.is.null.or.file.format.error`（zh/en 都缺）、`subRole.parse.subRole.info.failed`（zh/en 都缺）。
〔源：对 `$BE/src/main/resources/messages.properties` 与 `messages_en_US.properties` 的键集合比对〕

**非 i18n 的硬编码消息**：`SubRoleController.java:95` 直接 `Response.error(401, "The id can't be empty")`
——`code=401` 但 `msg` 是英文硬编码、且 **HTTP 状态码仍是 200**（`Response.error` 不是异常，走正常返回）
〔源：`$BE/controller/response/Response.java:21-26`、`$BE/controller/SubRoleController.java:94-96`〕。

---

## 5. 认证边界

**结论先行**：厂商 gaop-be **没有任何拦截器/过滤器**（全仓无 `addInterceptors`）〔源：14 号文档〕，
401 完全由**各端点自己调 `getCurrentUser`** 抛出来。因此 K1 的 70 条端点里**只有 9 条**需要会话，
**61 条匿名可 200**。

会话读取的两个入口：
- `UserService.getCurrentUser(req)`——读 cookie `gateway-token` → Redis `gateway-token:<token>`
  → `"<username>:<roleKey>"`；cookie 缺失 / Redis 无值 / 用户不存在 → `throw new UnauthorizedException()`
  〔源：`$BE/service/UserService.java:883-918`，抛点 `:888,:896,:903,:917`〕。
- `PublicController.getCurrentUserInfo(req)`——同一实现，额外 `setPassword(null)`
  〔源：`$BE/controller/pub/PublicController.java:408-411`〕。

**需要会话的 K1 端点（9 条）**：

| 端点 | 决定代码（file:line） |
|---|---|
| `GET /v1/user` | `$BE/controller/UserController.java:145` `this.userService.getCurrentUser(req)` |
| `POST /v1/user` | `$BE/controller/UserController.java:295` `this.userService.getCurrentUser(request)` |
| `DELETE /v1/user/{userId}` | `$BE/controller/UserController.java:320-321` → `publicController.getCurrentUserInfo(req)` |
| `DELETE /v1/user/all_user` | `$BE/controller/UserController.java:390-392` 循环调 `getDeleteUser(u, req)` → 同上 |
| `PUT /v1/user/{userId}` | `$BE/controller/UserController.java:406` → `publicController.getCurrentUserInfo(req)` |
| `GET /v1/role/software` | `$BE/controller/RoleController.java:101` `this.userService.getCurrentUser(req)` |
| `GET /v1/role/{roleId}/software` | `$BE/controller/RoleController.java:114` + `isAdmin` 校验 |
| `PUT /v1/role/{roleId}/software` | `$BE/controller/RoleController.java:125` + `isAdmin` 校验 |
| `POST /v1/role/{roleId}/software` | 内部转调 `roleSoftwareList(roleId,1,MAX,req)`（`$BE/controller/RoleController.java:144`）→ 同上 |

（上表 9 条。`RoleController` 的 4 条里，`GET /v1/role/{roleId}/software` 与
`PUT /v1/role/{roleId}/software` 还会额外抛 `role.no.permission.view` 做 admin 校验。）

**匿名 200 的 K1 端点（61 条）**，按 controller 分组（这些端点**完全不读会话**）：

- `UserController`（20 条）：`/list`、`/detailByUserId`、`/redirect`、`/login`、`/logout`、`/{userId}`(GET)、
  `/{userId}/roles`、`/{roleId}/users`、`/recovery`、`/backup`、`/export`、`/import/template`、`/import`、
  `/reset_user`、`/access_permission`、`/update_avatar/{userId}`、`/tenx_user`、`/get`、`/info`、`/userToRole`
  ——**包括 `POST /v1/user/logout` 和 `POST /v1/user/login`**。
  注：`POST /v1/user/login` 自身是认证入口，当然不要求会话；
  `POST /v1/user/logout` 只在 `req.getCookies()` 里遍历 `gateway-token`，无 cookie 时**静默返回 200**
  〔源：`$BE/service/UserService.java:392-448`〕。
- `RoleController`（23 条）：除上表 4 条之外的全部。
- `SubRoleController`（10 条，全部）：类内**零** `getCurrentUser`／`HttpServletRequest`
  〔源：`grep -n 'getCurrentUser\|HttpServletRequest' $BE/controller/SubRoleController.java` 零命中〕。
- `RegisterController`（1 条，全部）。
- `AuthController`（7 条，全部）：类内零会话读取。

> ⚠️ 这是**安全语义的关键事实**，不是笔误：厂商把授权**完全交给网关**
> 〔源：14 号文档；本档案在 5 个 controller 内逐端点复核无拦截器〕。
> 重写若在这些端点加 401，会**破坏**前端（前端在无会话时会拿到 401 而非空列表）。
> 反过来说：`GET /v1/user/{roleId}/users`、`/v1/user/info` 这类**返回全量用户名与状态**的端点
> 在厂商侧是**匿名可取**的。重写要"逐字节兼容"就必须同样匿名——这是一个必须显式决策的安全例外。

`GET /v1/user/access_permission` 是**半个例外**：它不调 `getCurrentUser`，而是自己遍历 cookie
（`$BE/service/UserService.java:1409-1429`）；但它的守卫是**死代码**——
`userLoginInfo` 在 `:1411` 被 `new` 出来，`:1427` 的 `if (userLoginInfo == null)` **永不成立**；
若 cookie 缺失或 Redis 无值，`:1421` 的 `user.split(":")` 会抛 `NullPointerException`
→ 被兜底 handler 变成 `code=-1` / HTTP 500〔源：`$BE/config/ResponseExceptionConfig.java:63-72`〕〔推断：
`user` 为 `null` 时 `.split` 必然 NPE〕。**即「未登录访问 access_permission 返回 500，不是 401」**——
这是"厂商把规则泄漏进 controller"的典型反面教材。

---

## 6. paas-core 现状对照

读的仓内文件：`$CORE/paas-core-trigger/src/main/java/com/csm/paas/core/trigger/http/{UserController,RoleController,AuthController}.java`、
`$CORE/paas-core-domain/src/main/java/com/csm/paas/core/domain/{user,role}/**`、
`$CORE/paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/{dao,adapter/repository}/**`。

### 6.1 已实现（7/70，另有 2 条 `/v1/pub` 别名）

| 厂商端点 | paas-core 位置 | 状态 |
|---|---|---|
| `GET /v1/user` | `…/trigger/http/UserController.java:59-66` `currentUser(request)` | **部分**（见 6.2） |
| `GET /v1/user/get` | `…/trigger/http/UserController.java:40-52` `loginList()` | **部分**（无分页/无 keyword/排序反了） |
| `POST /v1/user/login` | `…/trigger/http/AuthController.java:32-40`（`@PostMapping({"/v1/pub/login","/v1/user/login"})`） | **完整**（12 号文档：20/20 键、token UUID、Redis 键值一致〔运行实测〕） |
| `POST /v1/user/logout` | `…/trigger/http/AuthController.java:42-46`（`@PostMapping({"/v1/pub/logout","/v1/user/logout"})`） | 实现存在；**未在 12 号文档的实测表里**〔未定论〕 |
| `GET /v1/role` | `…/trigger/http/RoleController.java:30-36` | **部分**（忽略 `page/size/username`） |
| `GET /v1/role/list` | `…/trigger/http/RoleController.java:61-67` | **部分**（忽略 `page/size/roleName`；与 `/v1/role` 共用同一查询，代码注释已承认） |
| `GET /v1/role/software` | `…/trigger/http/RoleController.java:42-59` | **部分**（无会话时返回 `{list:[],total:0}`；厂商在"有会话但无战位"时返回 `{list:null,total:null}`） |

**信封**：`ResponseDTO{msg:String, code:Integer, data:T}`，`ok()` → `msg="OK", code=0`
〔源：`$CORE/paas-core-api/src/main/java/com/csm/paas/core/api/dto/ResponseDTO.java`〕——与厂商 `Response` 一致。

### 6.2 形状/语义不一致（必须修）

| # | 不一致 | 厂商 | paas-core | 证据 |
|---|---|---|---|---|
| D1 | `GET /v1/user` 的 `password` | **返回 BCrypt 串** | 恒 `null`（复用 `LoginAssembler`，其中 `.password(null)`） | 厂商 `UserController.java:147`（不置空）+ 样本 `runtime-samples/v1_user.json`；`$CORE/…/trigger/assembler/LoginAssembler.java:23` |
| D2 | `GET /v1/user` 的 `roles`/`subRoles`/`settingList` | **非 null**（装配了用户全部战位/子角色/`system.setting.flag`） | 恒 `null` | 厂商 `UserController.java:148-152`；`LoginAssembler.java:29` |
| D3 | `uuid` 类型 | `Integer`（`User.java:22`），值为 0..255 或 `-1` | `String` | 厂商 `model/User.java:22`；`$CORE/…/api/dto/LoginUserDTO.java:23`。样本里 `uuid=null` 故 e2e 未暴露〔源：`runtime-samples/v1_user.json`〕 |
| D4 | `GET /v1/user/get` 排序 | `ORDER BY created_at DESC` | DAO 是 `ORDER BY created_at`（**ASC**） | 厂商 `UserService.java:1814`；`$CORE/…/infrastructure/dao/IUserDao.java:18` |
| D5 | `GET /v1/user/get` 分页/keyword/`total` | `page/size` 生效；`total` 是 PageHelper 过滤后总数 | 忽略 `page/size/keyword`；`total = SELECT COUNT(*) FROM g_user` | 厂商 `UserService.java:1806-1825`；`$CORE/…/trigger/http/UserController.java:41-51` + `IUserDao.java:21-22` |
| D6 | `GET /v1/role` / `/list` 的分页与过滤 | `page/size/username/roleName` 生效，`total` 为过滤后总数 | 全部忽略，返回全量 | 厂商 `RoleService.java:269-309,361-399`；`$CORE/…/RoleController.java:30-36,61-67` |
| D7 | `GET /v1/role` 的 admin/dev 伪门户 | admin/dev 强制带 `softwareList=[{id:0,appId:"admin",appName:"运维管理员门户"}]` | 由 `RoleListAssembler` 处理（**需核对是否复现**） | 厂商 `RoleService.java:286-304`；`$CORE/…/trigger/assembler/RoleListAssembler.java`（本次未逐行读，见 §8） |
| D8 | `GET /v1/role/software` 的"无战位"分支 | `Response.ok(new RoleSoftwareList())` → `{list:null,total:null}` | `RoleSoftwareListDTO.builder().list(List.of()).total(0)` → `{list:[],total:0}` | 厂商 `RoleController.java:102-105`；`$CORE/…/RoleController.java:47-49` |

### 6.3 缺失（63/70）

**持久层缺口**：`$CORE` 只有 3 张 K1 表的 DAO ——
`IUserDao`（`g_user`）、`IRoleDao`（`g_role`、`g_role_software`）〔源：`IRoleDao.java:15-25`〕。
**没有** `g_user_role`、`g_user_login`、`g_sub_role`、`g_user_sub_role`、`g_sub_role_role` 的任何 DAO/PO/Mapper
〔源：`find $CORE -name '*Dao.java'` 全量清单〕。→ 关系表相关端点全部无处落地。

**未实现端点分组（63 条）**：
1. **用户 CRUD 与关联**（20 条）：`/v1/user` POST、`/{userId}` GET/PUT/DELETE、`/all_user`、`/list`、
   `/{userId}/roles`、`/{roleId}/users`、`/userToRole`、`/info`、`/detailByUserId`、`/tenx_user`、
   `/access_permission`、`/redirect`。
2. **用户导入导出**（5 条）：`/backup`、`/export`、`/import`、`/import/template`、`/recovery`、`/reset_user`。
3. **战位 CRUD 与关联**（18 条）：`/v1/role` POST/DELETE、`/{id}` GET/PUT、`/exist_role_name`、
   `/{roleId}/users`、`/{roleId}/person`、`/{roleId}/{userId}`、`/{roleId}/suRoles`、`/{roleId}/subRoles`、
   `/{roleId}/{subRoleId}/subRole`、`/updateSubRoleRole`、`/relate/app`、`/{roleId}/app/{appId}`、
   `/{roleId}/software` PUT/POST、`/switchRole`。
4. **战位导入导出**（6 条）：`/backup`、`/export`、`/import`、`/import/template`、`/recovery`、`/reset`。
5. **子角色全部 10 条**。
6. **`/register/ip/harbor`**（1 条）。
7. **`AuthController` 全部 7 条**（属 K3，不计入本上下文缺口）。

**测试覆盖**：K1 相关只有 2 个测试类 ——
`$CORE/paas-core-domain/src/test/java/com/csm/paas/core/domain/user/AuthServiceImplTest.java`（登录闭环 5 例）
与 `$CORE/paas-core-trigger/src/test/java/com/csm/paas/core/trigger/assembler/RoleListAssemblerTest.java`
〔源：`find $CORE -path '*src/test*' -name '*.java'`〕。→ **63 条缺失端点零测试**。

---

## 7. 缺口与风险（排序）

| # | 风险 | 等级 | 证据 |
|---|---|---|---|
| **R1** | **迁移不建 admin/dev 战位与 admin/root 用户**。只跑 87 个迁移的库没有 `g_role.admin`/`dev`，也没有 `g_user.admin`。而 D1/D2 之外，`GET /v1/user` 的 `roles` 与 `POST /v1/user/login` 都依赖 `g_role`/`g_user_role` 有数据。→ 重写必须有**自带种子**（或导入 live 库）。**这是本上下文最硬的前置依赖** | 🔴 阻塞 | `$MIG/V0.4.6:1-16`（admin/dev/root 的 INSERT 全被注释掉）；`$MIG/V0.5.2:7` 只插 `user` 战位；`grep 'INSERT INTO.*g_user' $MIG` 零激活命中 |
| **R2** | **既有证据 03 号文档 §4.0 的「只有 3 个 UNIQUE」是错的**：`g_user.username`、`g_role.key` 都有 DB 唯一索引（小写 `unique` 被大写 grep 漏掉）。若重写按"应用层判重即可"设计，会与真实 DB 行为不符（并发下 DB 会抛 `Duplicate entry`，而被当成 500 `database.error`） | 🔴 高 | `$MIG/V0.0.1:4,18`；`grep -rni 'unique' $MIG \| wc -l` = 9 |
| **R3** | **`GET /v1/user` 泄漏 BCrypt 口令哈希**。厂商原样返回 `g_user.password`；paas-core 当前置 `null`（D1）。若"逐字节兼容"是硬门槛则必须复现泄漏；若是安全修正则必须显式记为**已批准偏离**并更新验收基线。**两种选择都要写进 ADR，不能默认** | 🔴 高 | 厂商 `UserController.java:147`；样本 `runtime-samples/v1_user.json`；`$CORE/…/LoginAssembler.java:23` |
| **R4** | **61/70 端点匿名可达**（含返回全量用户名/状态的 `/v1/user/info`、`/v1/user/{roleId}/users`）。重写若沿用现在的"网关接管"，要把这条**写成显式契约与风险接受记录**；否则上线即等于把用户枚举暴露在网关白名单之外（取决于网关策略） | 🟠 中高 | §5 全表；`grep getCurrentUser` 在 5 个 controller 的命中清单 |
| **R5** | **9 条业务规则在 controller 里**（I2/I3/I5/I6/I7/I8/I9/I15/I22），且其中 I3 只覆盖 `admin`/`dev`、`cicd`/`root` 完全没保护（I4 被推翻）。重写若照搬 controller 逻辑，就会复制这套不一致 | 🟠 中高 | `$BE/controller/RoleController.java:218-244`；`$BE/controller/UserController.java:297-306,377-382,406-428,539-542`；`$BE/constant/RoleConstant.java:7-10` + 零引用 |
| **R6** | **关系表无 DB 唯一约束**：`g_user_role`、`g_user_sub_role`、`g_sub_role_role` 都可写重复行。而 vendor 的写法恰是"先全删再插"（I24），在并发下会产生重复。重写若不加唯一约束，就继承这个缺陷；加了则与厂商 DB schema 不同（但**行为更正确**，属于可接受偏离） | 🟠 中 | §2.3/2.6/2.7；`$BE/service/impl/SubRoleServiceImpl.java:348-357,371`；`$BE/service/RoleService.java:618` |
| **R7** | **`g_user_login` 永不写入**，只有级联 delete。R6 的"6 表级联"实测只有 5 张表有数据。重写需要建表+delete 以保持契约，但**不要**为它实现写入路径（否则会引入厂商没有的行为） | 🟡 低 | `grep -rn 'userLoginMapper\.' $BE` 仅 2 处 delete；`grep 'new UserLogin(' $BE` 零实体构造 |
| **R8** | **`g_user.uuid` 类型不符**（D3）：厂商 `Integer`，paas-core `String`。当前 e2e 样本 `uuid=null` 掩盖了它。一旦有 `uuid` 非空的用户，前端拿到的 JSON 类型就不同 | 🟡 低（但会静默） | `$BE/model/User.java:22`；`$CORE/…/LoginUserDTO.java:23`；`runtime-samples/v1_user.json` |
| **R9** | **`access_permission` 未登录时 NPE→500（非 401）**。重写最容易"顺手修成 401"，那会偏离厂商 | 🟡 低 | `$BE/service/UserService.java:1409-1429`（`:1427` 死守卫 + `:1421` 解引用） |
| **R10** | **`DELETE /v1/user/{userId}` 的控制器路径无事务**（I17）：`getDeleteUser` 无 `@Transactional`。删用户时若第二步失败，会留下孤儿关系行 | 🟡 低 | `$BE/service/UserService.java:815-834`；`$BE/controller/UserController.java:380` |
| **R11** | **子角色端点 100% 匿名**且 `DELETE /v1/sub_role/delete` 空列表返回 `code=401` 但 HTTP 200、`msg` 英文硬编码。这三个特征都反直觉，必须刻意复现 | 🟡 低 | `$BE/controller/SubRoleController.java:92-98`；§5 |
| **R12** | **前端 `GET /v1/user/getLoginUser` 指向另一个后端**（`/zk-102-jsxx-.../v1/user/getLoginUser`，硬编码 `window.location.hostname`）。它出现在 `umi` 的请求拦截器特例里。重写**不应**实现，但要知道它存在（否则会误以为前端报了 404） | 🟡 低 | `$FE/decompiled/umi.d08aa716.js`；调用页 `p__operate__home__index.a56e7a11.async.js` |
| **R13** | **21 条前端从不调用的端点是维护负担**：其中 `POST /v1/user/recovery` 实现体为空、`/export` 与 `/backup` 完全重复。重写可以**明确不实现**这 21 条中的一部分（尤其 `/recovery`、`/export`、`/tenx_user`、`relate/app`、`/access_permission`），但需一次决策 | 🟡 低 | §1.8 清单 |

---

## 8. 未定论

以下条目本档案**没有**证实，不猜。括号里是"什么能把它钉死"。

1. **实时库的索引事实**：`g_user.username` / `g_role.key` 的 `UNIQUE` 是迁移文本事实〔代码事实〕，
   但**未在 live 库上跑 `SHOW INDEX`**。`ALTER TABLE ... CONVERT TO CHARACTER SET` 是否可能改名/丢弃索引
   是〔推断〕。→ 在 `172.16.209.25` 上 `SHOW INDEX FROM g_user; SHOW INDEX FROM g_role;` 即可定论。
   （03 号文档的"无唯一约束"结论因此**被本档案按文本证据推翻**，但最终以 live 库为准。）
2. **`g_role.type` 的语义方向**：迁移回填是 `admin/dev → 2`、其余 `→ 1`〔代码事实〕，
   `RoleForm` 注释写「1战位 2角色」。但**前端 `switchRole` 传的 `type` 实际值**未从页面代码确认
   （`p__role.61a39591.async.js` 里 `type` 由调用点决定）。→ 抓一次前端请求即可。
3. **`birth` 的 JSON 序列化**：厂商 `User.birth` 是 `java.util.Date`，`JsonConfig.JACKSON` 未禁用
   `WRITE_DATES_AS_TIMESTAMPS`，故〔推断〕输出 epoch millis（`Long`）；但所有已录样本 `birth` 都是 `null`，
   **无法观测**。→ 造一个 `birth` 非空的用户在厂商上取一次响应。
4. **`GET /v1/user/logout` 后 Redis 的实际状态**：12 号文档实测了登录侧（会话键 + `login-list`），
   **没有** logout 的实测记录。`logout` 的 Redis 语义可见于 `$BE/service/UserService.java:425-426`
   （`deleteRedisKey(key)` + `deleteRedisKey(currentUser)`，其中 `currentUser` 取自
   `login-list` 的 `get(0)`——若列表为空会 `IndexOutOfBoundsException`）〔代码事实〕，
   但**行为未实测**。→ 无 cookie / 有 cookie 两次调 logout 各取一次响应 + `redis-cli --scan`。
5. **`RoleListAssembler` 是否复现 admin/dev 伪门户（D7）**：本次未逐行读 `$CORE/…/assembler/RoleListAssembler.java`
   的实现。→ 读该文件 + 跑 `RoleListAssemblerTest`，或 A/B 对比 `GET /v1/role` 的 `softwareList`。
6. **`GET /v1/role/switchRole` 的 `onLineUserList` 来源**：代码读 `redisService.getLoginUser("login-list")`
   〔源：`$BE/service/RoleService.java:207`〕，而 `RedisService.getLoginUser` 的前缀拼接方式本次未核对
   （是否与 `login-list:<user>:<role>` 的实际键型匹配）。→ 读 `$BE/service/RedisService.java` 的
   `getLoginUser`/`saveTokenAsList`。
7. **`g_sub_role` 是否有种子**：迁移无 INSERT〔代码事实〕，但 `g_sub_role_role` 的 `AUTO_INCREMENT=9547`
   说明实环境曾有大量行。这些行的来源（dump 导入？应用内置？）**未定论**。→ `SELECT COUNT(*) FROM g_sub_role;`
8. **`api-catalogue.json` 里 `POST /v1/sub_role` 的 `body=null`**：catalogue 未记录其 body 形状，
   本档案按 controller 的 `SubRoleUserForm` 记录〔代码事实〕。→ 若需前端实际载荷，抓页面请求。

---

## 附：本档案的可复现命令（只读）

```bash
# 端点总数（应输出 70）
python3 - <<'EOF'
import json
d=json.load(open('/Users/csm/Desktop/Project/msop/recovered-web/regenerated/openapi-gaop-be.json'))
pref=('/v1/user','/v1/role','/v1/sub_role','/register','/auth')
print(sum(1 for p in d['paths'] if p.startswith(pref) for _ in d['paths'][p]))
EOF

# UNIQUE 事实核查（应输出 9 行，其中 2 行是 g_user.username / g_role.key）
grep -rni 'unique' /Users/csm/Desktop/Project/msop/artifacts/k8s/sql/db/migration/

# 迁移里没有任何激活的 g_user / admin-dev g_role 插入
grep -rn 'INSERT INTO' /Users/csm/Desktop/Project/msop/artifacts/k8s/sql/db/migration/ | grep -i 'g_user\|g_role'

# 需要会话的 K1 端点
grep -n 'getCurrentUser\|getCurrentUserInfo' \
  /Users/csm/Desktop/Project/msop/recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/{UserController,RoleController,SubRoleController}.java
```
