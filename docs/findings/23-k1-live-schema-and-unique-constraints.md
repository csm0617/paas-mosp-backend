# K1（用户/战位）**实库**表结构与唯一约束——含对 03 号证据的一处更正

**证据等级**：〔运行实测〕。来源：`172.16.209.25:30306` 的 `gaop` 库，`information_schema`（2026-10-14，切片 3 开工前）。
采集命令（可复现）：

```bash
kubectl -n default exec mysql-deployment-5f85c97448-pxbhc -- mysql -ugaop -pgaop123456 -D gaop -B -e "
SELECT table_name,column_name,column_type,is_nullable,column_default,extra
FROM information_schema.columns WHERE table_schema='gaop'
AND table_name IN ('g_user','g_role','g_user_role','g_user_login','g_sub_role','g_user_sub_role','g_sub_role_role')
ORDER BY table_name,ordinal_position;"
```

## 0. 一处更正（重要）

`docs/findings/03-context-evidence.md` §4 的这句话**是错的**：

> "其余 36 张表**没有任何唯一约束**：`g_software.key`、`g_role.key`、`g_user.username`、`g_service_port`（同服务下端口唯一）全部只靠应用层 `select` 后判断。"

实库查询（`non_unique=0`）结果：

| 表 | 唯一索引 | 列 |
| --- | --- | --- |
| `g_role` | `PRIMARY` | `id` |
| `g_role` | **`key`** | `key` |
| `g_user` | `PRIMARY` | `id` |
| `g_user` | **`username`** | `username` |
| `g_sub_role` | `PRIMARY` | `id` |
| `g_sub_role` | **`key`** | `key` |
| `g_user_role` / `g_user_login` / `g_user_sub_role` / `g_sub_role_role` | `PRIMARY` | `id` |

**结论**：`g_role.key`、`g_user.username`、`g_sub_role.key` **是数据库强制的唯一约束**（DDL 出处：`V0.0.1__init_table.sql:18` 的 `key varchar(36) unique`；`g_user.username` 与 `g_sub_role.key` 同理由早期迁移与 `V0.7.7__add_sub_role_tables.sql` 建出，且无任何迁移 drop 它们）。

**这对重写的直接影响**（三处，都不是细节）：

1. **唯一性不变量在并发下是成立的**（对这三个键），与 03 号证据的悲观判断相反。重写版**不应**只靠"select 后判断"——那会与厂商一样在竞态下抛 `SQLIntegrityConstraintViolationException`，而**厂商的 `user.username.already.exists` / `role.key.identifier.existed` 只在非竞态路径生效**。兼容做法：先 select 判重（给出厂商的 i18n 错误键），再让 DB 兜底并**把兜底异常也映射到同一个错误键**——这样行为是厂商的超集（厂商在竞态下是 500）。
2. **不能靠"无唯一约束"来论证可以放宽**。`g_software.key` / `g_service_port` 是否真的无约束**未实测**（本次只查了 K1 的 7 张表），不得外推。
3. 切片 3 的领域模型里，"用户名唯一""战位 key 唯一"应建模为**领域规则 + DB 兜底**，而不是纯应用层判断。

**更正方法记录**：03 号证据是从 DAO/源码"看不到 `@Table` 唯一注解"推出的结论，没有查 DDL 与实库。**教训与 11 号证据同源：源码读出来的约束条件，要用实库/DDL 复核。**

## 1. 七张表的实库列（切片 3 的 DDL 事实）

### `g_user`
| 列 | 类型 | NULL | 备注 |
| --- | --- | --- | --- |
| `id` | varchar(36) | NO | PK |
| `username` | varchar(36) | YES | **UNIQUE** |
| `password` | varchar(100) | YES | BCrypt（`$2a$…`） |
| `status` | tinyint | NO | 0=禁用 / 1=启用（`UserConstant.Status`） |
| `email` | varchar(100) | YES | |
| `birth` | date | YES | |
| `phone` | varchar(15) | YES | |
| `desc` | text | YES | |
| `updated_at` | bigint | YES | 毫秒 |
| `created_at` | bigint | YES | 毫秒 |
| `avatar` | mediumtext | YES | base64 |
| `api_token` | varchar(256) | YES | 时速云 token（`/v1/user/tenx_user` 用） |
| `tenx_username` | varchar(128) | YES | |
| `last_login_role` | varchar(128) | YES | 最后登录战位 key |
| `nickname` | varchar(128) | YES | |
| `uuid` | int | YES | **uuid 数据额度**（`user.data.limit.exceeded` 用） |

### `g_role`
`id` varchar(36) PK / `key` varchar(36) **UNIQUE** / `name` varchar(128) **NOT NULL** / `desc` text / `updated_at` bigint / `created_at` bigint / **`type` int**（NULL 允许）

**`type` 语义（〔运行实测〕本环境数据 + 〔代码事实〕）**：`type=1` = **战位**，`type=2` = **角色**。
实据：本库 `g_role` 两行——`admin`(type=1, name=运维管理员门户)、`user`(type=2, name=席位操作员)；
`RoleService.java:1036` 新建时 `r.setType(1)`；`RoleService.java:180` 的 `getUserRoleNameByType(username, type)` 按 `role.type = type` 过滤，
而 `UserService.java:1853-1854` 把它分别填进 `UserInfoList.roles`(type=1) 与 **`UserInfoList.belongUsers`(type=2)**——**后者是厂商的字段名错位**（"belongUsers"里装的是 type=2 的角色名）。契约比对必须按厂商的错位复刻。

### `g_user_role`
`id` bigint PK auto_increment / `user_id` varchar(36) / `role_id` varchar(36) / `updated_at` / `created_at`
FK：`user_id→g_user.id`、`role_id→g_role.id`（**无 ON DELETE CASCADE**）

### `g_user_login`
`id` bigint PK auto_increment / `user_id` varchar(36) / `role_id` varchar(36) / `token` varchar(36) / `logout_at` bigint / `created_at` bigint
**无外键**（所以删用户/战位时它不会被 DB 级联，必须由应用清）

### `g_sub_role`
`id` varchar(36) PK / `key` varchar(36) **UNIQUE** / `name` varchar(128) **NOT NULL** / `desc` text / `updated_at` / `created_at`

### `g_user_sub_role`
`id` varchar(36) PK / `user_id` varchar(36) / `sub_role_id` varchar(36) / `updated_at` / `created_at`
**无外键**

### `g_sub_role_role`
`id` bigint PK auto_increment / `sub_role_id` varchar(36) / `role_id` varchar(36) / `updated_at` / `created_at`
FK：`sub_role_id→g_sub_role.id`、`role_id→g_role.id`（**无 ON DELETE CASCADE**）

## 2. 外键全貌（决定"级联删除"为什么必须由应用做）

只有 4 条外键，且**全都没有 `ON DELETE CASCADE`**：

```
g_user_role.user_id     → g_user.id
g_user_role.role_id     → g_role.id
g_sub_role_role.sub_role_id → g_sub_role.id
g_sub_role_role.role_id     → g_role.id
```

⇒ 删战位时若不先删 `g_user_role`/`g_sub_role_role`，DB 会直接拒绝（`Cannot delete or update a parent row`）。
这就是 03 号证据所说的"6 表级联删除必须原子"的**机制层原因**，重写版必须保留同一顺序并放在一个事务里。

## 3. 本环境 K1 数据现状（做 e2e 时的基线）

| 表 | 行 |
| --- | --- |
| `g_role` | 2：`admin`(type=1)、`user`(type=2) |
| `g_user` | 1：`admin`（status=1，last_login_role=admin，nickname=管理员） |
| `g_user_role` | 1：admin→admin |
| `g_sub_role` / `g_user_sub_role` / `g_sub_role_role` / `g_user_login` | 0 |

**没有 `dev` 战位**（与厂商默认初始化不同），`/usermanage/role` 页在本环境只有 2 行、`/usermanage/account` 只有 1 行——e2e 断言要按这个基线写，不能按厂商默认数据写。
