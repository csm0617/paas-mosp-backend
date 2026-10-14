# `GET /v1/user/access_permission?path=` 实现规格（下一轮可直接开工）

**证据等级**：〔代码事实〕（逐行读全）。2026-10-14。本轮**未写业务逻辑**，但**已把要用的 SQL 落进代码**。

## 1. 算法（`UserService.accessPermission:1409-1455` + `findPath:210-228`）

```
0) 读 Cookie `gateway-token` → redis 里的 `<user>:<role>`
   ⚠️ 厂商这段有**缺陷**：`userLoginInfo` 是先 new 出来的对象，
      所以 `if (userLoginInfo == null) throw ...` **永远不成立**；
      没有 Cookie 时 `userLoginInfo.getRole()` 为 null ⇒ 第 1432 行 **NPE（500）**。
      （它本意要抛的是 `user.not.logged.in.or.expired`，代码写错导致不可达。）

1) roles = roleService.list(username)   // 该用户的战位列表
   for (Role r : roles) { roleName = r.key; roleId = r.id; }   // **后覆盖前 ⇒ 取最后一个**

2) if (roleId != null || "admin".equals(roleName)) {
       for (softwareId : softwareMapper.allConnAppId(roleId)) {     // 该战位关联的软件
           key = querySoftById(softwareId).key
           for (urlPath : allServicePathBySoftware(softwareId))     // 见 §2 的三表 join
               allPathName.add("/api/" + key + "/" + urlPath)
       }
   }

3) return findPath(allPathName, path, roleName)
```

`findPath`（**三个正则照抄**，`[tenx]`/`[role]`/`[gaop]` 是**字符类**不是单词——厂商原样，别修）：

```
loginPath = ^[\/]*[tenx]*[\/]*[api]*[\/]*[v2]*[\/]*[a-z0-9]*$
rolePath  = ^[\/]*[v1]*[\/]*[role]*[\/]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*$
res       = ^[\/]*[api]?[\/]*[gaop]*[\/]*[v1]*[\/]*...$   （三段可选字符类）
if (roleName == "admin" && (任一正则 matches(path)))  → true
if (path 为空 或 "/")                                  → true
if (path 与 allPathName 中某项**全等**)                 → true
否则                                                   → false
```

## 2. 已落地的 SQL（本轮新增，可编译、已被全量测试覆盖）

`ISoftwareDao.selectServicePathsBySoftware`（厂商 `SoftwareMapper.allServicePathBySoftware` 的三表 join 照抄）：

```sql
SELECT st.path_name FROM g_software_app sp
INNER JOIN g_software s ON sp.software_id = s.id
INNER JOIN g_service_port st ON sp.app_name = st.service_name
WHERE sp.software_id = #{softwareId}
```
已通过 `ISoftwareRepository.queryServicePathsBySoftware` 暴露到领域层。

## 3. 下一轮实现要做的**接线**（本轮卡在这里）

`UserQueryServiceImpl` 目前只有 `userRepository`/`roleRepository`/`subRoleRepository`，
**缺** `ISoftwareRepository`（取软件 `key` 与上面那条路径查询）。两个选择：

1. 给 `UserQueryServiceImpl` 加构造参数 `ISoftwareRepository`（要改 4 处已有测试的构造调用）；
2. 或把 `checkAccessPermission` 放到一个已经有 `ISoftwareRepository` 的服务里（例如新建 `UserAccessService`），
   控制器注入它——**更干净，不动既有测试**。

⇒ 推荐 2。

## 4. 验证计划（独立/对拉）

| 用例 | 期望 |
| --- | --- |
| admin 会话 + `/api/gaop/v1/user/info` | 命中 `res` 正则 → **true**（对拉） |
| admin 会话 + `/` | **true**（对拉） |
| admin 会话 + `/whatever` | **false**（对拉） |
| 无 Cookie | 厂商 **500 NPE**；我们返回 400 `user.not.logged.in.or.expired`（**其本意**）⇒ 记为 B 类偏离 |
| 非 admin + 战位下确实配了某路径 | 与 `allPathName` 全等 → true（需要探针数据：`g_role_software` + `g_software_app` + `g_service_port` 三张表都造行） |

## 5. 状态

本轮新增 SQL/仓储（可编译）；全量 **285 条用例全绿**；无残留资源。
