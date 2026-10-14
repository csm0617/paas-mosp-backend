# `GET /v1/sub_role/{id}` 与 `DELETE /v1/sub_role/delete` 规格 + 实现（第 154 轮）

**证据等级**：〔代码事实〕（厂商 `SubRoleController.subRoleDetail:163-174` / `deleteSubRoleById:92-98` +
`SubRoleServiceImpl.detail:341-344` / `deleteByIds:277-291` / `deleteSubRoleInRoleBySubRoleId:454-456` 逐行读）
+〔运行实测〕（真库三张表）。2026-10-14。

## 1. 两个端点

```java
@GetMapping("/{id}")  public Response<SubRole> subRoleDetail(@PathVariable String id)
@DeleteMapping("/delete") public Response<Boolean> deleteSubRoleById(@RequestBody SubRoleIdsForm form)
```
`SubRole{id, key, name, nameFr, nameUs, updatedAt, createdAt, desc, userList, roleList}`；
`SubRoleIdsForm{subRoleIdList}`。

## 2. 流程与照抄点

### 2.1 `GET /{id}`

```
subRole = selectByPrimaryKey(id)          // **只按主键取**（不填 userList/roleList）
if (subRole != null):
    nameUs = i18n 自动名("sub_role","en_US",key)；**为空 ⇒ 兜底成 name**
    nameFr = i18n 自动名("sub_role","fr_FR",key)；**为空 ⇒ 兜底成 name**
返回 subRole（查不到 ⇒ data 为 null）
```
**照抄点**：`userList`/`roleList` **不填**（实测① 两个键都是 `null`）；`nameUs`/`nameFr` 兜底成 `name`。
（i18n 覆盖机制本环境未实现，等价于"取不到 ⇒ 兜底"，与既有 `/v1/role/{id}` 的处理一致。）

### 2.2 `DELETE /delete`

```
body 为 null 或 subRoleIdList 为空 ⇒ **code=401 + "The id can't be empty"**（HTTP 仍是 **200**！见下）
逐个 id（**不校验存在性、也不报错**）：
    若该子角色存在且 key 非空白 ⇒ 删它的 i18n 自动名（本环境未实现，跳过）
    删 g_sub_role_role where sub_role_id = ?
    删 g_sub_role by primary key
    删 g_user_sub_role where sub_role_id = ?
**恒返回 true**
```
**照抄点**：① 删**三处**（子角色行 + 战位关联 + 用户关联）；② 对不存在的 id **不报错**（实测③ 传了 `ghost` 仍 `true`）；
③ 空入参的"401"是**响应体里的 `code`**，HTTP 状态码仍是 **200**（厂商 `Response.error(...)` 不抛异常，实测④）。

## 3. 实现与验证

**实现**：`ISubRoleQueryService.querySubRoleDetail` + 新增 `ISubRoleCommandService`（`deleteSubRoles`）+
`SubRoleController` 两个端点 + `SubRoleIdsDTO`；DAO/仓储加 `selectById` / `deleteById` /
`deleteSubRoleRolesBySubRoleId` / `deleteUserSubRolesBySubRoleId`（**扩展现有** `ISubRoleDao`，
没有新建平行 DAO）。

### 3.1 离线：`SubRoleDetailAndDeleteTest` 6 例，全量 **549** 条 0 失败

覆盖：详情填 `name` 兜底且两个集合为 null；查不到 ⇒ null；删除三处都动；
**未知 id 也返回 true 且照样执行删除**；null 列表不碰库；多 id 按序。

### 3.2 在线：真库（seed `g_sub_role{sr-probe}` + `g_user_sub_role` + `g_sub_role_role` 各一行）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `GET /v1/sub_role/sr-probe` | `200`；`id/key/name/desc` 正确，**`nameUs`/`nameFr` 等于 `name`**，**`userList`/`roleList` 为 `null`** |
| ② | `GET /v1/sub_role/ghost` | `200`，`code 0`、`data:null` |
| ③ | `DELETE /delete {"subRoleIdList":["sr-probe","ghost"]}` | `200 data:true`；**三张表残留全为 0** |
| ④ | `DELETE /delete {}` | HTTP **200**，响应体 **`{"msg":"The id can't be empty","code":401,"data":null}`** |

### 3.3 收尾

探针三表行已删（`g_sub_role`/`g_user_sub_role`/`g_sub_role_role` 均 0）、临时 NodePort 与后端进程已回收。

> 备注：本轮 seed 的中文没用 `--default-character-set=utf8mb4`，终端回显是乱码；但接口按原值往返，
> 与端点实现无关（既有教训：seed 中文必须带该参数）。
