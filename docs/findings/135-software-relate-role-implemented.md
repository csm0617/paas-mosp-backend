# `PUT /v1/software/softwareRelateRole` 规格 + 实现（第 142 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:708-713` + `SoftwareService.updateSoftwareRelateRole:1535-1561` +
`softwareRelateRole:587-604` + `RoleService:734-736` 逐行读）+〔运行实测〕（真库 + **真 WebSocket 推送**）。2026-10-14。

## 1. 端点

```java
@PutMapping("/softwareRelateRole")
public Response<Boolean> updateSoftwareRelateRole(@RequestParam String softwareId,
                                                  @RequestBody List<String> roleKeyList)
```
body 是 **roleKey 数组**（不是 id）。

## 2. 三步流程

```
① roleList = LinkedHashSet(roleKeyList)          // **去重**
② 查 g_role_software where app_id = softwareId 得到已有 roleId：
     逐个：delete g_role_software where role_id = ? and app_id = softwareId
           **每删一条推一次 WebSocket**：convertAndSend("/topic/software", {event:"DELETE", roleList:<roleId>})
③ roleKeyList 里逐个 roleKey → 查 g_role by key（**查不到就跳过**）→ roleIdList
   再 softwareRelateRole(softwareId, roleIdList)：
     逐个 roleId：若 (roleId, appId) 已存在 ⇒ 跳过
                 否则 insert {roleId, appId, createdAt=now, order = **该战位现有行数 + 1**}
                  （`insertSelective` **不设 app_name** ⇒ 该列为 NULL）
恒返回 true（即使 roleKeyList 为空也只是"删光不加"）
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **先删后加，且每删一条推一次 WS** | 推送次数 = 被删的关联条数（不是"一次批量推送"） |
| 2 | **WS 载荷是 `{event, roleList}`，`roleList` 是字符串** | 厂商 `SoftwareUpdate.roleList` 声明为 `String`（尽管名字叫 list），值是**单个 roleId** |
| 3 | `order = 该战位**现有行数** + 1` | 不是"最大 order + 1"；删完再加时 order 会从头开始（实测：原 order=7 的行被删后重插为 **1**） |

另外：厂商用的是**一个 static 共享对象** `RoleService.softwareStatus`（多请求会互相覆盖）；
我们每推一次构造新载荷——对外可观测的载荷形状不变。

## 4. 实现与验证

**实现**：`ISoftwareCommandService.updateSoftwareRelateRole` + 控制器端点；
新增 DAO/仓储 `deleteRoleSoftwareRelation(roleId, softwareId)`；**复用**已有的
`queryRoleIdsBySoftwareId` / `relateRoles`（后者本来就实现了"已存在跳过 + order=行数+1 + insert"）；
新增端口 `ISoftwareRelationBroadcastPort` + trigger 实现 `SoftwareRelationBroadcaster`（推 `/topic/software`）。

### 4.1 离线：`SoftwareRelateRoleTest` 5 例，全量 **463** 条 0 失败

覆盖：删已有 + **每删一条推一次**；重复 key 去重 + 未知 key 跳过；空/null keyList 仍会删已有；
**删除发生在插入之前**；没有已有关系 ⇒ 不推送。

### 4.2 在线：真库 + 真 WebSocket（seed `g_software{sw-rr}` + `g_role_software{role_id=3(user), app_id=sw-rr, order=7}`）

| 例 | 实测 |
| --- | --- |
| ① WS 探针（`tools/ws-software-probe.js`，订阅 `/topic/software`）+ `PUT ... ["user"]` | 收到 `MESSAGE destination:/topic/software`，载荷 **`{"event":"DELETE","roleList":"3"}`** ⇒ 第 3 节 2 实测确认 |
| ② 同一次调用的库效果 | 调用前 `{role_id=3, order=7}` → 调用后 `{role_id=3, order=1}` ⇒ 旧行被删、重插且 **order 回到 1**（第 3 节 3 实测确认） |
| ③ `[]` | `200 data:true`，`g_role_software` 该软件 **0 行** ⇒ "只删不加" |
| ④ `["user","ghost"]` | `200 data:true`，只有 role 3 一行 ⇒ 未知 key 跳过 |
| ⑤ `["user","user"]` | `200`，**只有 1 行** ⇒ 去重 |

### 4.3 收尾

探针 `g_role_software`/`g_software` 行已删（`g_software`=0、`g_role_software`=0、`g_role`=2）、
临时 NodePort 与后端进程已回收。

> **新增验证资产**：`tools/ws-software-probe.js`（订阅 `/topic/software` 的 STOMP 探针，与第 114 轮的
> `ws-userinfo-probe.js` 同款）。这是本会话第二个"把 WS 推送当断言"的探针。
