Type: task
Status: open
Blocked by: 07

## Question

实现厂商认证的**最小闭环**，这是"厂商前端一行不改跑通"这条门槛的前置条件：

- `POST /v1/pub/login` 入参 `{username,password,role}`，BCrypt 校验（`g_user.password`），下发 `Set-Cookie: gateway-token=<token>; Path=/`
- 该 cookie 的校验方是 **gaop-gateway-auth**（`/auth`），它决定请求能否进到业务后端
- 会话状态在 Redis（`login-list:<user>:<roleKey>` / `gateway-token:<token>`，见 ADR 边界外的既有结论）

## 研究已完成：契约全清楚（`docs/findings/12-auth-contract.md`）

token = `UUID`（不透明串），Redis 键 `gateway-token:<uuid>`，值 `<username>:<战位key>`，
`RPUSH login-list:<username>:<战位key>`，cookie `gateway-token=<uuid>; Path=/`，
ttl 取 `g_setting.system.permission.expired_time`（本集群 1440 分钟 → 实测 TTL 86400s），
两侧 `geneKey` 字节级一致，Redis 同为 `gaop-redis:6379` 且未配置 database。

**由此可推导（待你确认或否决）**：既然 ADR-0001 的目的地是**只替换 gaop-be**，gateway-auth 原样保留，
那"自带校验"就不是选项——我们**必须**签发与它兼容的会话。ticket 里原来的问题 1 因此变成
"确认沿用 gateway-auth 作为校验方"，问题 2 的答案是"必须一致"。

若你希望改成自带校验（连带替换 gateway-auth），那要另开 ADR，因为那会改目的地。

## 验收

在 172.16.209.25 上实测：浏览器登录后打 `paas-core` 的 `/v1/service` 能通过认证并返回 200；退出登录后返回 401。

## Answer
