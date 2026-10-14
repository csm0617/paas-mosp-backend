# `POST /v1/user/update_avatar/{userId}` 实现：写入正确、**icon 逻辑实测复现**、原值已恢复

**证据等级**：〔运行实测〕（可回滚探针 + 日志）+〔离线单测〕。2026-10-14。

## 1. 实测（探针写在 `admin` 身上，**用完已恢复原值**）

| 用例 | 结果 |
| --- | --- |
| 上传小 PNG | 200；`data` = **用户详情**（`avatar`/`belongRole`/… 等键）✅ |
| 库内 `g_user.avatar` | `iVBORw0KGgoAAAAAAAAAAAAAAAAAAA…` = **我们上传文件 base64 的前 30 位，完全一致** ✅ |
| **同一文件再传一次** | 日志里 `icon` 从 **`CHANGE`** 变成 **`UN-CHANGE`** ✅ ——**这就是厂商 md5 比较逻辑被复现的直接证据** |
| WebSocket 推送 | 两次都打出了 `广播 userInfo: {"event":"MODIFY","sendStatus":true,"userInfo":{…,"icon":"…"}}` ✅ |
| `>200KiB` 文件 | 400「头像不能大于200KiB」✅ |
| 不存在的用户 | 400「用户不存在」（`user.not.found`）✅ |
| **恢复** | `g_user.avatar` 已还原为 `NULL`（上传前就是 NULL）✅ |

**一处探针自身的坑**：第一次跑时我用 `UID=$(…)` 存用户 id —— `UID` 在 bash 里是**只读变量**，
赋值失败 ⇒ `$UID` 是 shell 的数值 UID ⇒ 端点正确地返回了 400「用户不存在」。
**看起来像实现问题，其实是探针问题**（本会话同类错误第 3 次：`gaop` 空列表、`/v1/user/list` 的 401、这次的 `UID`）。

## 2. 实现（六步照抄）

1. 控制器：`>204800` → 400 `global.avatar.size.limit`；算 `base64` 与 `md5`；
2. 域服务：查用户（不存在 → 400 `user.not.found`）；
3. `icon`：旧头像与 md5 **都非空**时才比较"旧头像解码后的 md5"与"本次 md5" ⇒ 相同 `UN-CHANGE`、否则 `CHANGE`；
   任一为空 ⇒ `CHANGE`；
4. 组装 `{event:"MODIFY", sendStatus:true, userInfo:{userId,username,nickname,status,icon}}` 并**推 `userInfo` 通道**；
5. `g_user.avatar = base64`（只更新这一列）；
6. 返回用户详情（复用既有装配）。

**分层处置**：domain 模块**没有 Jackson**（本会话第 3 次踩到）⇒ 端口签名改为接收**结构化 `Map`**，
序列化与推送由 trigger 层的 `UserInfoBroadcaster` 完成（目的地 `/topic/userInfo`，broker 前缀见 `StompWebSocketConfig`）。

## 3. 状态

端点映射 **+1**；测试 **41 类 300 → 304 条全绿**；`g_user.avatar` 已恢复；临时 NodePort 已删、进程清零、工作树干净。
