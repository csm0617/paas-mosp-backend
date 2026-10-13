Type: task
Status: open
Blocked by: 07

## Question

实现厂商认证的**最小闭环**，这是"厂商前端一行不改跑通"这条门槛的前置条件：

- `POST /v1/pub/login` 入参 `{username,password,role}`，BCrypt 校验（`g_user.password`），下发 `Set-Cookie: gateway-token=<token>; Path=/`
- 该 cookie 的校验方是 **gaop-gateway-auth**（`/auth`），它决定请求能否进到业务后端
- 会话状态在 Redis（`login-list:<user>:<roleKey>` / `gateway-token:<token>`，见 ADR 边界外的既有结论）

必须先回答的边界问题（可由 07 一并定）：
1. 重写后端是否**自带** cookie 签发与校验，还是继续依赖 gaop-gateway-auth（那就是"只替换 gaop-be"的多组件边界）？
2. 若自带，Redis 里的键名/结构必须与厂商一致吗（否则 gaop-gateway-auth 认不出来）？

## 验收

在 172.16.209.25 上实测：浏览器登录后打 `paas-core` 的 `/v1/service` 能通过认证并返回 200；退出登录后返回 401。

## Answer
