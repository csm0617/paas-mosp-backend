# `/topic/userInfo` 推送**端到端验证**：订阅端真的收到了（补上第 109 轮的验证缺口）

**证据等级**：〔运行实测〕（真实 STOMP 订阅 + HTTP 触发 + 收到 MESSAGE 帧）。2026-10-14。

## 1. 补的是什么缺口

第 109 轮实现 `update_avatar` 时，我验证到"**日志里打出了 `广播 userInfo: {...}`**"就收尾了——
那只能证明**代码走到了推送那一行**，**证明不了订阅端收到**（`convertAndSend` 到不存在的目的地同样会打日志）。
本轮把这个缺口补上。

## 2. 方法（探针已入库：`tools/ws-userinfo-probe.js`）

```
① 用 `ws` 连 SockJS 裸 WS 端点  ws://127.0.0.1:8080/ws/websocket
② 手工发 STOMP 帧：CONNECT → SUBSCRIBE destination:/topic/userInfo
③ 通过 HTTP 触发一次 POST /v1/user/update_avatar/{userId}
④ 断言收到 destination:/topic/userInfo 的 MESSAGE 帧
```

## 3. 结果

```
收到 MESSAGE:
MESSAGE
destination:/topic/userInfo
content-type:text/plain;charset=UTF-8
subscription:sub-0
message-id:5a6fb19f-…-0
content-length:177

{"event":"MODIFY","sendStatus":true,"userInfo":{"userId":"2222…","username":"admin",
 "nickname":"…","status":1,"icon":"CHANGE"}}
```

⇒ **订阅端确实收到了**，且载荷与厂商 `UserStatus` 的形状一致（`event`/`sendStatus`/`userInfo{userId,username,nickname,status,icon}`）✅
（探针写库后已恢复：`admin.avatar` 回到 `NULL`。）

## 4. 顺带确认的两件事

- **SockJS 裸 WS 端点可用**：`/ws/websocket`（前端走 SockJS，探针走裸 WS，两条路都通）；
- **STOMP 目的地是 `/topic/userInfo`**（broker 前缀 `/topic` + 厂商的通道名 `userInfo`），与厂商 `sendInfo(text,"userInfo")` 的语义对齐。

## 5. 状态

新增工具 `tools/ws-userinfo-probe.js`；本轮无业务代码改动；全量 **304 条用例全绿**；临时资源已清理。
