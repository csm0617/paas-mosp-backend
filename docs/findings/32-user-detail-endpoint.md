# 切片 3a 读路径：`GET /v1/user/detailByUserId`（用户管理编辑抽屉的前置）

**证据等级**：〔运行实测〕（对拉厂商，**整体 JSON 相等**）+〔代码事实〕。2026-10-14。

## 1. 结果

```
mine    detailByUserId: HTTP 200 code=0 keys=17
vendor  detailByUserId: HTTP 200 code=0 keys=17
整体 JSON 相等: True          ← 逐字段全等，含嵌套对象
mine    不存在用户    : HTTP 400 code=400 msg=找不到该用户
vendor  不存在用户    : HTTP 400 code=400 msg=找不到该用户
```

**17 个键**（顺序照抄样本）：
`id, username, nickname, status, email, birth, phone, updatedAt, createdAt, desc, avatar, lastLoginRole, uuid, roles, subRoles, admin, belongRole`

## 2. 为什么这个端点值得单独写一篇：**同名不同形状**

| 字段 | `/v1/user/info`（用户列表） | `/v1/user/detailByUserId`（编辑抽屉） |
| --- | --- | --- |
| `roles` | **字符串数组**（战位名） | **Role 对象数组**（16 键） |
| `subRoles` | 字符串数组（空时是 `null`） | SubRole **对象**数组（空时是 `[]`） |
| `belongUsers` / `belongRole` | `belongUsers`（字符串，装的是 type=2 的名字） | `belongRole`（**Role 对象**） |
| `admin` | 无 | 有（布尔） |
| 键数 | 11 | 17 |

⇒ **两个端点不能共用一个 DTO**。这也是为什么 `docs/findings/25` 里要专门记一条：
用户管理页的 `roles` 有**两种形状**。本轮把这条从"证据里的提醒"变成了"两个各自对齐的 DTO"。

## 3. 填充规则（照抄 `UserService.getUserDetail`，`:1756-1778`）

- `roles` = `RoleService.listByRoleType(username, 1)` ⇒ 带 `softwareList` + **admin/dev 伪门户覆盖**，其余集合 null；
- `belongRole` = 同上但 `type=2`；
- `subRoles` = `SubRoleService.listByUserId` ⇒ **不填** `userList`/`roleList`（JSON 里是 `null`）；
- `admin` = `roles` 里是否有 key=`admin`（`hasAdminPermission`，`:851-861`）；
- 用户不存在 → **`user.missing.user`**（"找不到该用户"），注意**不是** `user.not.found`。

**顺带做了一次小重构**：把"admin/dev 伪门户覆盖 + 三个布尔"从 `RoleQueryServiceImpl` 的私有方法提成
`domain/role/service/RolePortalSupport`，让 `/v1/role*` 与 `/v1/user/detailByUserId` 共用**同一份**厂商语义，
避免两处各写一遍后慢慢分叉。同时保留差异：`/v1/role/{id}` **不做**伪门户覆盖（实测 `softwareList` 为 null），
所以调用方要显式选择调哪个方法。

## 4. 未做 / 待办

- **页面级 e2e 未做**：本轮证据是**接口级逐字节相等**（比点一次"编辑"更强），
  但"点击编辑 → 抽屉打开"这条交互链路还没有在真实前端上跑过。下次跑 `/usermanage/account` 时顺带覆盖。
- `PUT /v1/user/{userId}`（修改）仍未实现，下一轮；规则已读齐：
  - 权限：`username == admin` → 任意；有 admin 战位 → **不能改 admin 用户**（`role.cannot.modify.super.op.admin.storage`）；
    否则**只能改自己**，改别人 → **HTTP 500** `role.normal.user.cannot.modify.other.user`；
  - 域规则：`user.not.found`；口令空则保持、否则长度 6~36（`user.change.password.invalid`）；
    用户名空则保持、否则查重（`user.change.username.exist`）+ 长度 2~36（`user.change.username.invalid`）；
    email >36 → `user.mail.invalid`；desc 空白 → **空串 `""`**（与新增的置 `null` 不同）、>255 → `user.change.desc.invalid`；
  - **厂商会把 email/birth/phone 强制写回原值**（`UserService.java:716-718`）⇒ 这三个字段**改不动**，照抄；
  - 关系：`userListKey`/`roleListKey` 为 null 或空 → **删除**该类型关联，否则替换；`subRoleListId` 为空 → 删除全部子角色关联。
