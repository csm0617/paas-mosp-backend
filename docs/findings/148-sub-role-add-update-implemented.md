# `POST /v1/sub_role` 与 `PUT /v1/sub_role/update` 规格 + 实现（第 155 轮）

**证据等级**：〔代码事实〕（厂商 `SubRoleController.addRole:59-70` / `updateRole:85-88` +
`SubRoleServiceImpl.existKeyName:108-110` / `addRole:113-140` / `updateSuRole:233-260` / `usersRelateRole:144-160` /
`duplicateKey:361-363` / `duplicateName:365-367` 逐行读）+〔运行实测〕（真库）。2026-10-14。

## 1. 两个端点

```java
@PostMapping                 public Response<SubRole> addRole(@RequestBody SubRoleUserForm form)
@PutMapping("/update")       public Response<Boolean> updateRole(@RequestBody SubRoleUpdateReq req)
```
表单字段：`{id, key, name, nameFr, nameUs, desc, userList}`（新增忽略 `id`）。

## 2. 新增流程（含一处**跨表**怪点）

```
控制器先查重：existKeyName(key, name) —— **查的是 `g_role`（战位表）**！
   命中 ⇒ 400 `sub.role.storage.existed`（带 key、name）
name 空或 >100 ⇒ 400 `sub.role.name.invalid.length`
key 空或 >36  ⇒ 400 `sub.role.unique.identifier.invalid.length`
desc 空白 ⇒ 存 **null**；desc >255 ⇒ 400 `sub.role.desc.invalid.length`
id = UUID；createdAt = updatedAt = now；insert
userList 非空 ⇒ **去重**后逐个关联（**用户 id 不存在则跳过**）
返回再查一次库的子角色
```

**跨表怪点（实测①）**：`existKeyName` 用的是 `subRoleMapper`，但 where 写的是
`RoleDynamicSqlSupport.role.key/name` ⇒ SQL 落在 **`g_role`** 上。
所以**用一个已存在的战位 key（`user`）去新增子角色会被拒**：
`400 子角色的唯一标识user或名称随便一个名字已存在`。

## 3. 修改流程

```
按 id 取（**不存在 ⇒ 400 `sub.role.storage.not.found`**，见 §4）
key 与别的子角色重复 ⇒ 400 `sub.role.key.exist`；name 与别的重复 ⇒ 400 `sub.role.change.name.exist`
name/key 非 null 时校验长度（>100 / >36 或 <1）⇒ 两个 400；desc 空白 ⇒ null、>255 ⇒ 400
updatedAt = now
**先删光该子角色的用户关联，再按 userList 去重关联**
最后 `updateByPrimaryKeySelective`（只写非 null 字段）
```

**照抄点（实测⑥'）**：修改时**不带 `userList` 会把已有用户关联全部清空**——因为 `usersRelateRole`
第一步就是"删光该子角色的关联"，空列表就什么都不插。实测：1 → 0。

## 4. 两处主动收紧（`deviations.md` B24）

厂商这两处会在正常业务路径上抛 NPE ⇒ 500：

| # | 厂商写法 | 后果 | 我们的处理 |
| --- | --- | --- | --- |
| 1 | `updateSuRole` 先调 `getSuRoleById(id)`，而后者是 `selectOne(...).orElse(null)).getKey()` | id 不存在 ⇒ **NPE ⇒ 500**，后面那句 `if (role == null) throw 400 sub.role.storage.not.found` **永远不可达** | 抛它**本来想表达的** 400 `sub.role.storage.not.found`（实测④） |
| 2 | `updateSuRole` 里 `userList.addAll(req.getUserList())` | `userList` 为 null ⇒ **NPE ⇒ 500** | 按**空列表**处理（即"清空关联"，与传空数组等价） |

## 5. 实现与验证

**实现**：`ISubRoleCommandService.addSubRole/updateSubRole` + `SubRoleController` 两个端点 +
`SubRoleFormVO`/`SubRoleFormDTO`；DAO/仓储（**扩展** `ISubRoleDao`/`SubRoleRepository`）加
`countRoleByKeyOrName` / `countByKeyExcludingId` / `countByNameExcludingId` / `insertSubRole` /
`updateSubRoleSelective` / `deleteUserSubRolesByUserId` / `insertUserSubRole` / `countUserById`。

### 5.1 离线：`SubRoleAddUpdateTest` 9 例，全量 **558** 条 0 失败

覆盖：新增的跨表查重、name/key 长度、desc 空白→null 与超长、只关联存在的用户（去重）；
修改的 id 不存在⇒400、key/name 重复、长度校验、**重建关联**、`userList` 为 null 不炸。

### 5.2 在线：真库

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 新增 `{key:"user", …}`（`user` 是 `g_role` 里已有的 key） | `400 子角色的唯一标识user或名称随便一个名字已存在` ⇒ **跨表查重实测确认** |
| ② | 新增 `{key:"probe-sr2", name:"探针子角色2", desc:"   "}` | `200`；库里 `desc` 是 **NULL**（空白 ⇒ null） |
| ③ | `PUT /update {id, name:"改后的名字"}` | `200 true`；库里 name 已改 |
| ④ | `PUT /update {id:"no-such"}` | `400 子角色不存在`（B24 第 1 条） |
| ⑤' | 新增 `{key:"probe-sr4", userList:[<admin 的真实 id>, "ghost"]}` | `200`；`g_user_sub_role` 关联数 **1**（`ghost` 被跳过） |
| ⑥' | 对 ⑤' 的子角色 `PUT /update {id, desc:"d2"}`（**不带 userList**） | `200 true`；关联数 **1 → 0** ⇒ 第 3 节照抄点实测确认 |

### 5.3 收尾

探针子角色（`key LIKE 'probe-sr%'`）与其用户关联已删（`g_sub_role`=0、`g_user_sub_role`=0）、
临时 NodePort 与后端进程已回收；本轮不写库（除探针本身）。

> 小插曲：第一次跑 ⑤ 时用了 `UID=$(...)` 取用户 id——`UID` 在 bash 里是**只读变量**，
> 赋值失败后 `$UID` 展开成进程 uid（501），于是"用户不存在"被跳过、关联数为 0。改用 `ADMIN_ID` 后正常。
> 这个坑本会话早前踩过一次，值得记住。
