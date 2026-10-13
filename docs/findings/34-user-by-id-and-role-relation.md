# 切片 3a 写路径：按主键查用户 + 两个"战位配置"端点

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

本轮补齐三个端点，K1 里"用户"这一侧的读+写基本闭环：

| 端点 | 实测结果 |
| --- | --- |
| `GET /v1/user/{userId}` | **整体 JSON 相等**（14 键，`password` 为 null）；不存在 → 400 "查询无此用户ID" |
| `POST /v1/user/userToRole` | 响应与**库内关系条数**两侧一致（`1:1 2:1`）；不存在 → 400 "找不到改用户信息，无法进行关联操作" |
| `POST /v1/user/{userId}/roles` | 响应 `data:null` 与库内关系（`type=1` 被替换、`type=2` 保留）两侧一致；不存在 → 400 "用户不存在" |

## 1. 三个**互不相同**的"找不到用户"文案（这是本轮最容易踩的坑）

同一个"用户不存在"场景，厂商在不同路径上用了**四个不同的键**，全部实测过：

| 路径 | 键 | 文案 |
| --- | --- | --- |
| `DELETE /v1/user/{id}` | `user.id.not.found` | 查无此id的用户 |
| `GET /v1/user/{id}` | `user.id.query.not.found` | 查询无此用户ID |
| `POST /v1/user/{id}/roles` | `user.not.found` | 用户不存在 |
| `POST /v1/user/userToRole` | `user.missing.user.info` | 找不到改用户信息，无法进行关联操作 |

**四个键、四条路径、四种文案**。任何"统一成一个 `user.not.found`"的整理都会破坏其中三条。
上一轮我已经在删用户路径上因为读源码读错而吃过一次亏（`user.not.found` vs `user.id.not.found`），
这轮直接把四个都摆出来对拉。

## 2. 两个"战位配置"端点的语义差异（名字很像，行为不同）

| | `POST /v1/user/userToRole` | `POST /v1/user/{userId}/roles` |
| --- | --- | --- |
| 请求体 | `{userId, roleIdList}` | `[roleId, ...]` |
| 删什么 | 该用户**全部** `g_user_role` | 只删 `g_role.type=1` 的关联 |
| 传什么 | **roleId**（不校验是否存在，直接插） | **roleId**，但**逐个校验存在性**，不存在就跳过 |
| 去重 | `HashSet` 去重 | 不去重（`rolesRelate2User` 不去重） |
| 响应 | `true` | `null` |
| 找不到用户 | `user.missing.user.info` | `user.not.found` |

**实测的库内效果**（探针用户，`1:1 2:1` 表示 type=1 一条、type=2 一条）：

```
初始（只有 type2）          : 2:1
userToRole [admin,user]     : 1:1 2:1     ← 两侧相同
{id}/roles [admin]（重置后）: 1:1 2:1     ← type=2 保留，只换了 type=1
```

**一处"看起来像 bug 但照抄"的细节**：`userToRole` **不校验** roleId 是否存在（`UserRelationRole` 直接 insert），
而 `{userId}/roles` 走 `rolesRelate2User`，对每个 id 先 `selectByPrimaryKey`、找不到 `continue`。
所以同样传一个不存在的 id，两个端点的结果不同（前者插进去、后者跳过）。已分别实现并写进单测。

## 3. 实现落点

- `GET /v1/user/{id}`：`userRepository.queryById` + `UserAssembler`（口令置 null，与厂商一致）；
- `userToRole` → `IUserUpdateCase.replaceUserRoles`（去重 + `replaceAllUserRoleRelations`）；
- `{id}/roles` → `IUserUpdateCase.relatePositions`（`IRoleRepository.filterExistingIds` 过滤后只替换 type=1）；
- 新增 `IRoleRepository.filterExistingIds`，对应厂商 `rolesRelate2User` 里的逐个存在性检查。

## 4. 测试

- 域/用例单测：去重、空列表只删、两个端点各自的错误键、type=1 只替换 type=1；
- 离线 IT：三个端点的形状/文案/库内关系条数；
- 对拉：三个端点全部与厂商一致（见上表）；
- 全量 `mvn test` **118 条全绿**（上一轮 112）。
