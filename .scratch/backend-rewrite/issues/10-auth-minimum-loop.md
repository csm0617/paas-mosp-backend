Type: task
Status: resolved
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

**已实现并在集群上验证**（2026-10-14 03:1x），契约依据 `docs/findings/12-auth-contract.md`：

- `POST /v1/pub/login` 与 `/v1/user/login`（同一 handler，照厂商）：BCrypt → UUID token → `SETEX gateway-token:<uuid> <ttl> "<user>:<战位>"`
  → `RPUSH login-list:<user>:<战位>` → `Set-Cookie: gateway-token=<uuid>; Path=/`；ttl 读 `g_setting.system.permission.expired_time`（本集群 1440 分钟）。
- `POST /v1/pub/logout` 与 `/v1/user/logout`：删会话键并移出 login-list。
- 受保护端点（`/v1/role`、`/v1/service`）显式调用 `AuthSupport.requireSession`，未登录 → HTTP 401 + `{"msg":"未认证","code":401}`
  （照厂商——厂商也没有全局拦截器，401 是各端点自己抛的）。
- **五步在线验证全过**，最关键的一条：**厂商 gaop-be 认我方签发的 cookie 并返回 200**。

**口径说明**：实现沿用"gateway-auth 作为校验方"的方案——这是 ADR-0001（只替换 gaop-be）的直接推论。
若你决定改成自带校验（连带替换 gateway-auth），本工单需要重做，且要另开 ADR。

## Answer
