# `GET /v1/user/list` 实现规格（下一轮可直接开工）

**证据等级**：〔代码事实〕（两侧 DTO 字段逐项对照）。2026-10-14。本轮**不写业务代码**（上下文预算已紧，且这是"读清再写"的第二次实践）。

## 1. 与 `/v1/user/info` 的关系：**同一个查询，不同的条目形状**

| | `/v1/user/info` | `/v1/user/list` |
| --- | --- | --- |
| 厂商方法 | `userService.getUserList` | `userService.list` |
| 外层 | `UserInfoResponse{list,total}` | `UserRoleList{list,total}` |
| 条目 | `UserInfoList`（**11 字段**） | `UserRoles`（**16 字段**） |
| 是否要会话 | 否 | 否 |

**条目字段逐项对照**（这就是实现的核心信息）：

```
UserInfoList(11): id nickname username status updatedAt createdAt desc lastLoginRole roles subRoles belongUsers
UserRoles(16)   : id username nickname status email birth phone updatedAt createdAt desc avatar lastLoginRole uuid roles subRoles admin
```

⇒ `UserRoles` = 去掉 `belongUsers`，加上 **`email` `birth` `phone` `avatar` `uuid`** 与 **`admin`**（布尔）。

## 2. 厂商 `userService.list:836-848` 的规则

```
PageHelper.startPage(page,size)
likeKeyword = keyword 空 ? null : "%"+keyword+"%"
若 likeKeyword 长度 > 38 → 400 user.invalid.search.keyword.length     ← 注意：是**带 % 的串**算长度
select from g_user where username like ? order by created_at desc
getUserRoles(userList) → List<UserRoles>（含 admin = 该用户任一战位 key 为 "admin"）
total = Page.getTotal()
```

`admin` 的算法：`hasAdminPermission(roles)` → 遍历角色，任一 `key == "admin"` 即 true。

## 3. 下一轮要改的地方（已经定位到文件）

1. `UserInfoEntity`（domain）**缺 5 个字段**：`email` / `birth` / `phone` / `avatar` / `uuid`
   ⇒ 加字段 + 在 `IUserDao` 的查询里补这 5 列（现有查询只取了 11 个字段对应的列）；
2. 新增 `UserRolesDTO`（16 字段）+ `UserRoleListResponseDTO{list,total}` + 一个装配器
   （`admin` 由 `roles` 里是否含 `"admin"` 推出）；
3. 控制器加 `GET /v1/user/list`（与 `/v1/user/info` 共用同一个域查询）；
4. **关键字长度校验**：`keyword` 去空格后，若 `"%" + kw + "%"` 的长度 > 38 → 400 `user.invalid.search.keyword.length`
   （这个"含 % 算长度"的细节照抄）。

## 4. 验证计划

| 用例 | 期望 |
| --- | --- |
| `?page=1&size=20` | 与厂商**逐字节相等**（当前库 1 个用户；注意 `roles`/`subRoles` 的形状） |
| `?keyword=adm` | 相等 |
| 超长 keyword（>38 含 %） | 两侧都 400 `user.invalid.search.keyword.length` |
| `?page=2` | 相等（空列表） |

**注意**：厂商 `/v1/user/list` **不返回口令哈希**（`UserRoles` 里没有 `password`），所以不存在 `/v1/user/{roleId}/users` 那种"厂商返回哈希、我们置 null"的偏离。

## 5. 状态

本轮无业务代码改动；全量 **289 条用例全绿**；无残留资源。
