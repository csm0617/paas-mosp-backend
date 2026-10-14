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

## 2'. 读 `getUserRoles:1780-1803` 后的三处**关键修正**（比字段表更容易踩）

1. **`nickname` 永远为 null**：`getUserRoles` **没有** `setNickname`（尽管 `UserRoles` 声明了该字段）
   ⇒ `/v1/user/list` 的 `nickname` 恒 null，与 `/v1/user/info`（那里有值）**不同**。
   照抄实现时**不要**顺手从实体填 nickname——那会造成值差异。
2. **`roles` / `subRoles` 是"对象列表"不是字符串列表**：分别是
   `roleService.list(username).getList()`（`List<Role>`）与 `subRoleService.listByUserId(userId).getList()`（`List<SubRole>`）。
   而 `/v1/user/info` 的 `roles`/`subRoles` 是**字符串**（已逐字节验证）⇒ **两个端点的同名键形状不同**，
   实现前必须先把 `Role`/`SubRole` 的序列化形状取到样本（否则形状必错）。
3. **不过滤 root**：`list()` 的查询里没有 `!= root` 的条件（`/v1/user/info` 才过滤 root）。
   ⇒ 我们的 `queryUserInfoPage` 里那句 `filter(u -> !ROOT_USERNAME.equals(...))` **不能复用**，需要一个新的域方法。

另外一条好消息：`selectPageByKeyword` 的 SQL **已经取了全部 14 列**（含 `email/birth/phone/uuid/avatar`），
所以**不用改 SQL**——只需给 `UserInfoEntity` 补 5 个字段并让 `toInfo` 填上。

## 2''. 已取到**厂商实时样本**（`runtime-samples/v1_user_list.json`）——又一处规模修正

```json
{"list":[{"id":"...","username":"admin","nickname":null,"status":1,"email":null,"birth":null,"phone":null,
          "updatedAt":...,"createdAt":...,"desc":"...","avatar":null,"lastLoginRole":"admin","uuid":null,
          "roles":[{"id":"1111...","key":"admin","name":"...","nameFr":null,"nameUs":null,"updatedAt":...,
                    "createdAt":...,"desc":"...","type":1,"onLineUserList":null,"softwareList":...,
                    "userList":...,"subRoleList":...,"adminOrDev":...,"admin":...,"dev":...}],
          "subRoles":[], "admin":true}],"total":1}
```

三处**实测确认 / 规模修正**：

1. **`nickname` 实测就是 `null`**（印证 §2' 第 1 条）；
2. **`roles` 是完整 16 键的 `Role` 对象**（`id,key,name,nameFr,nameUs,updatedAt,createdAt,desc,type,
   onLineUserList,softwareList,userList,subRoleList,adminOrDev,admin,dev`）——**与 `/v1/role` 返回的 Role 形状相同**
   ⇒ 实现时应当**复用 `/v1/role` 那套角色装配**（含 `softwareList`/`userList`/`subRoleList` 的填充规则）。
   **这不是"加 5 个字段 + 一个 DTO"的规模**，而是要接上角色域的装配链路。
3. **`subRoles` 是 `[]`（空数组）不是 `null`**——与 `/v1/user/info` 的写法（空则 null）**不同**，照抄要注意。

⇒ **结论：`/v1/user/list` 的真实成本 ≈ "用户分页查询" + "每个用户套一遍角色装配"**，
应按这个规模排期（一轮实现 + 一轮对拉），不要按"别名端点"估。

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
