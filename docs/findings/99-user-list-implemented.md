# `GET /v1/user/list` 实现：**四组用例对拉完全相等**（含 16 键条目与超长 keyword）

**证据等级**：〔运行实测〕（对拉）+〔代码事实〕。2026-10-14。

## 1. 实测

| 请求 | 结果 |
| --- | --- |
| `?page=1&size=20` | **完全相等** ✅（16 键条目，`roles` 是完整 Role 对象、`nickname` null、`subRoles` `[]`、`admin` true） |
| `?page=1&size=20&keyword=adm` | **完全相等** ✅ |
| `?page=2&size=20` | **完全相等** ✅ |
| 超长 keyword（40 个字符 ⇒ `"%" + kw + "%"` 长度 42 > 38） | 两侧都 400，**文案逐字相同**：「搜索长度不合法，搜索关键字(用户名)的长度应在0~36个字符」✅ |

## 2. 实现（按 `findings/98` 的规格）

- **新建 `IUserRolesQueryService` + `UserRolesQueryService`**（依赖 `IUserRepository` + `IRoleQueryService` + `ISubRoleRepository`）：
  `roles` 要走上 `/v1/role` 同一套装配，而 `UserQueryServiceImpl` 没有 `IRoleQueryService`
  ⇒ 塞进去会牵连 4 处既有测试的构造调用（同 `findings/92` 的处置）；
- 域实体 `UserRolesEntity` **故意不含 `nickname`** —— 厂商 `getUserRoles` 从不 set 它，
  让"填错"在类型层面就不可能发生（测试里写 `getNickname()` 直接编译不过，这就是当时发现的）；
- 装配器 `UserRolesAssembler` 复用 **`RoleListAssembler.toItemDTO`** 产出 `roles`，
  并显式留空 `nickname`、空集合兜底成 `List.of()`；
- 查询**不过滤 root**（`/v1/user/info` 才过滤），`total` 用独立 count。

## 3. 一处**如实标注的未验证形状**

`subRoles` 在本环境是 `[]`，所以**非空时的形状未经对拉验证**。目前映射了 `SubRoleEntity` 的全部标量字段
（`id/key/name/nameFr/nameUs/updatedAt/createdAt/desc`），但**没有**映射 `userList`/`roleList`（那两个是嵌套列表）。
⇒ 若将来出现带子角色的用户，需要按厂商 `subRoleService.listByUserId` 的样本补齐并重跑对拉。

## 4. 状态

端点映射 **61 → 62**；测试 **41 类 289 → 293 条全绿**；临时 NodePort 已删、进程清零、工作树干净。
