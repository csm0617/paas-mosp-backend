# `POST /v1/sub_role/userAddRole/{userId}` 与 `POST /v1/sub_role/roleAddUser/{roleId}` 规格 + 实现（第 156 轮）

**证据等级**：〔代码事实〕（厂商 `SubRoleController.userAddRole:101-108` / `roleAddUser:110-118` +
`SubRoleServiceImpl.userAddRole:346-359` / `roleAddUser:369-382` 逐行读）+〔运行实测〕（真库关联表）。2026-10-14。

## 1. 两个端点（方向相反的一对）

```java
@PostMapping("/userAddRole/{userId}")   // body SubRoleIdsForm{subRoleIdList}
@PostMapping("/roleAddUser/{roleId}")   // body UserIdsForm{userIdList}
```

## 2. 流程（两个都是"先删光再插"，都恒返回 true）

```
userAddRole(userId, subRoleIdList)：
    删 g_user_sub_role where user_id = userId        ← 删**该用户**的全部关联
    逐个 subRoleId：insert {id=UUID, userId, subRoleId, createdAt=updatedAt=now}
    **不校验子角色是否存在**
    return true

roleAddUser(subRoleId, userIdList)：
    删 g_user_sub_role where sub_role_id = subRoleId ← 删**该子角色**的全部关联
    逐个 userId：insert {id=UUID, userId, subRoleId, createdAt=updatedAt=now}
    **不校验用户是否存在**
    return true
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **两个方向都是"先删光再插"** | 所以"只传一个 id"会把该用户/子角色原有的关联**全清掉**（实测②） |
| 2 | **`userAddRole` 不校验子角色是否存在** | 实测① 传 `ghost`（不是子角色）照样插入 |
| 3 | **`roleAddUser` 不校验用户是否存在** | 实测③ 传 `ghost-user`（不是用户）照样插入——**这与 `usersRelateRole`（新增/修改子角色时用的那个）相反**：后者对每个 userId `selectByPrimaryKey`，查不到就跳过 |

> 第 3 点是个容易踩的对照：同一个控制器里，`POST /`（新增子角色）与 `PUT /update` 走
> `usersRelateRole`（**跳过不存在的用户**），而 `POST /roleAddUser/{roleId}` 直接插（**不检查**）。

## 4. 实现与验证

**实现**：`ISubRoleCommandService.userAddRole/roleAddUser` + `SubRoleController` 两个端点 + `UserIdsDTO`；
**复用**上一轮已有的 `deleteUserSubRolesByUserId` / `deleteUserSubRolesBySubRoleId` / `insertUserSubRole`
三个仓储方法——本轮**没有新增任何 DAO 方法**。

### 4.1 离线：`SubRoleAddUpdateTest` 12 例（本轮 +3），全量 **561** 条 0 失败

新增三例：`userAddRole` 先删后插且**不校验子角色存在**（`userExists` 从未被调用）；
`userAddRole(null)` 只删不插；`roleAddUser` 先删后插且不校验用户存在。

### 4.2 在线：真库（seed `sr-rel`/`sr-rel2` 两个子角色）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `POST /userAddRole/<admin> {"subRoleIdList":["sr-rel","ghost"]}` | `200 true`；admin 的关联 = `ghost, sr-rel` ⇒ **不存在的子角色也插了** |
| ② | 再调 `{"subRoleIdList":["sr-rel2"]}` | `200 true`；admin 的关联 = **只剩 `sr-rel2`** ⇒ 先删光再插 |
| ③ | `POST /roleAddUser/sr-rel {"userIdList":["<admin>","ghost-user"]}` | `200 true`；sr-rel 的关联 = `admin + ghost-user` ⇒ **不存在的用户也插了** |
| ④ | `POST /roleAddUser/sr-rel2 {"userIdList":[]}` | `200 true`；sr-rel2 关联数 = **0** ⇒ 清空 |

### 4.3 收尾

探针子角色与关联行已删（`g_sub_role`=0、`g_user_sub_role`=0）、临时 NodePort 与后端进程已回收；本轮不写库。
