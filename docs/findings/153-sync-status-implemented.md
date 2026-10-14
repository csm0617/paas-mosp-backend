# `GET /v1/sync/status` 规格 + 实现（第 160 轮）

**证据等级**：〔代码事实〕（厂商 `SyncController:151` 的 `static SyncStatus syncStatus = SyncStatus.init()` +
`:252-255` + `SyncStatus:24-40` 逐行读）+〔运行实测〕（真环境，形状逐键比对）。2026-10-14。

## 1. 端点

```java
@GetMapping("/status")
public Response<SyncStatus> getSyncStatus()      // 直接返回 **static 单例**
```
`SyncStatus{syncing, success, currentSyncName, currentSyncType, current, total, errorStatus}`。

## 2. 初始形状（`SyncStatus.init()`）

```
new SyncStatus(false, false, 0, null)   // 只显式给这四个；其余取类型默认
⇒ {syncing:false, success:false, currentSyncName:null, currentSyncType:null,
   current:0, total:0, errorStatus:null}
```

## 3. 照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | 状态是**进程内单例**（`static`） | 我们用一个 Spring 单例持有同一个对象；多次调用返回**同一实例**（单测断言 `assertSame`） |
| 2 | `init()` 只显式设四个字段 | 其余（`currentSyncName`/`currentSyncType`/`total`）保持类型默认 ⇒ JSON 里是 `null`/`0` |

## 4. 实现范围（诚实说明）

**已实现**：`GET /status`。
**未实现**（本文件与 `SyncController` 的注释里都写明）：
- `POST /v1/sync`（真正的同步：依赖 SSH/rsync 与对端节点，代码上千行）；
- `GET /v1/sync/database/exist`（要打对端节点的 `/api/gaop/v1/sync/database/status`）；
- `/base_url`、`/{roleId}/users`、`/user`、`/role`、`/subRole`、`/software`、`/nodePortService` 等对端拉取端点。

⇒ 因为 `POST /v1/sync` 未实现，这个状态**恒为初始值**（`syncing=false`）——
这与厂商"没有同步在跑时"的可观测结果一致；一旦将来实现同步，只需在同一个单例上改状态即可。

## 5. 验证

### 5.1 离线：`SyncStatusTest` 3 例，全量 **578** 条 0 失败

覆盖：初始形状与厂商 `init()` 逐字段一致；`init()` 显式设的四个字段；**多次调用返回同一实例**。

### 5.2 在线：真环境

| 项 | 实测 |
| --- | --- |
| 信封 | `200`、`code:0` |
| 键集合 | 恰为 `['current','currentSyncName','currentSyncType','errorStatus','success','syncing','total']` |
| 与厂商 `SyncStatus.init()` 形状**逐键比对** | **完全一致**（`True`） |
| 再次调用 | 数据完全一致（单例语义） |

### 5.3 收尾

临时 NodePort 与后端进程已回收；本轮不写库。
