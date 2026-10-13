Type: prototype
Status: open
Blocked by: 01, 04

## Question

用一次**纵向切片**回答"JDK 21 + DDD 六边形 + fabric8 7 在真实数据上到底行不行"：在 `rewrite/` 里实现「软件-服务-端口」域的一条只读链路（列表 + 详情），数据源为 172.16.209.25 的 `gaop-namespace`。

必须验证并记录：

- fabric8 7 读真实 Deployment/Service，**具名端口**（`targetPort: http`、`tcpSocket.port: http`）不再抛 `Cannot deserialize Integer from String`
- 返回 JSON 与厂商 `/api/gaop/v1/software/overView`、`/v1/software` 的形状一致（diff 证据）
- 分层落地正确：Trigger → API → Case → Domain ← Infrastructure；Repository 实现走 `infrastructure/adapter/repository/`，fabric8 调用走 `infrastructure/gateway/` + `infrastructure/adapter/port/`

## 验收

- `curl` 实测输出与厂商响应 diff 一致（贴证据）。
- 失败模式写进 `## Answer`——它决定后面所有域的实现方式。
- 本 ticket 的产物是**可丢弃**的验证代码；转正由 07 决定。

## Answer
