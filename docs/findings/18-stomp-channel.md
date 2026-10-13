# STOMP over SockJS 通道已打通（2026-10-14 04:00）

## 为什么必须做它

前端的**工作负载/网络/容器**这些页面**不走 HTTP 取数**——它们的数据来自 STOMP 推送：

- 客户端：`I.webSocketFactory = function(){ return new SockJS(L + "/ws") }`（`umi.d08aa716.js`），订阅 `/topic/k8s/proxy/{Service,Deployment,Pod}`；
- 消息体：`{type, object}`，`type ∈ ADDED/MODIFIED/DELETED/ERROR`，`object` 是 K8s 对象；页面按事件增量更新。
- 证据链的另一半：这些页面的源码里**一个 HTTP URL 字面量都没有**（只数 HTTP 会误判"页面没有数据来源"）。

## 实现（按六边形摆位）

| 层 | 组件 | 职责 |
| --- | --- | --- |
| domain | `cluster/adapter/port/IK8sEventPort` | 定义 `startWatch(namespace, BiConsumer<kind, json>)` / `stopWatch()` |
| infrastructure | `gateway/K8sWatchGateway` | fabric8 watch Service/Deployment/Pod，把事件转成厂商同形 `{type, object}` |
| infrastructure | `adapter/port/K8sEventPort` | 实现端口，持有 watch 句柄（只调 gateway） |
| trigger | `websocket/StompWebSocketConfig` | `@EnableWebSocketMessageBroker` + `addEndpoint("/ws").withSockJS()` + `/topic` 简单 broker |
| trigger | `websocket/K8sTopicBroadcaster` | 把事件广播到 `/topic/k8s/proxy/<Kind>` |

**两个设计决定**（都不是随手写的）：

1. **端口放在 domain、实现放 infrastructure**：一开始我把广播器直接依赖 `infrastructure` 的网关，**编译不过**——
   因为 trigger 按分层只能依赖 api/case/domain。这不是麻烦，是分层在提醒我把通信放到端口上。
2. **watch 在 `ApplicationReadyEvent` 里启动，且失败不阻断启动**：离线契约测试（无集群）也必须能起上下文；
   顺带这是生产上也更稳的行为。
3. 自建的 `/ws/info` 桩**已删除**——`withSockJS()` 自带该端点与全部传输路径。

## 实证（后端日志）

```
K8sWatchGateway   : 已启动 namespace:gaop-namespace 的 Service/Deployment/Pod watch
K8sEventPort      : K8s watch 已启动: [Service, Deployment, Pod]
K8sTopicBroadcaster: K8s 事件广播已就绪，topic 前缀 /topic/k8s/proxy/
SimpleBrokerMessageHandler: BrokerAvailabilityEvent[available=true]
WebSocketMessageBrokerStats: WebSocketSession[4 current WS(0)-HttpStream(3)-HttpPoll(1)],
                             stompSubProtocol[processed CONNECT(1)-CONNECTED(1)-DISCONNECT(0)],
                             outboundChannel[... completed tasks = 1]
```

前端控制台：**`socket 连接成功`**。
SockJS 侧：`GET /ws/info` 200、`POST /ws/<server>/<session>/xhr` 200、`POST .../xhr_send` 204。

**即：厂商前端经 SockJS 降级到 xhr-polling 成功建立 STOMP 会话，服务端 watch 在跑并已向外发过消息。**

> 注：WebSocket 升级失败（`Handshake failed due to invalid Upgrade header: null`）是**我的本地 harness 不支持升级**导致的，
> SockJS 自动降级到 xhr-polling 完全正常；真实网关下会直接走 WS。

## 仍然没成的：工作负载表还是 0 行

关掉"快速入门"弹窗（点了"知道了"）后仍然没有数据行。当前还剩三个 404：

| 端点 | 语料库 |
| --- | --- |
| `GET /v1/image/projectList?page=1&size=999` | ✅ 已有厂商样本 |
| `GET /v1/role/software` | ✅ 已有厂商样本 |
| `GET /v1/pub/etcd_label/check` | ❌ 未录，需抓 |

**未定论**：不能确定是哪一条拦住了表格渲染（也可能与 STOMP 事件的 `type` 期望有关）。
下一轮先补这三个端点再复测；若仍为 0 行，就按"页面在等哪条消息"继续查（例如给 `/topic/k8s/proxy/Deployment` 手动推一条 ADDED 看页面反应）。
