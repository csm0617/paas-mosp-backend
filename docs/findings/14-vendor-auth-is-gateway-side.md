# 厂商的鉴权在网关侧，不在 gaop-be：我上一轮加错了 401

**2026-10-14 实测（无 cookie 直连厂商 gaop-be）**：

| 端点 | 厂商返回 | 说明 |
| --- | --- | --- |
| `GET /v1/role` | **200** | 无 cookie 也能拿到战位列表 |
| `GET /v1/service` | **200** | 无 cookie 也能拿到服务列表 |
| `GET /settings` | **200** | 公开 |
| `GET /v1/mode/current` | **200** | 公开 |
| `GET /v1/user/get` | **200** | 公开（登录页的账号下拉靠它） |
| `GET /v1/role/list` | **200** | 公开 |
| `GET /v1/user` | **401** | 这是应用级校验的少数端点（`UserService.getCurrentUser`） |

## 结论

`gaop-be` **没有全局拦截器**（全仓无 `addInterceptors`）；只有少数端点自己调 `getCurrentUser` 并抛 401。
真正的门在 `gaop-gateway-auth`（`LoginAuthServiceImpl` 读 `gateway-token` cookie → 查 Redis）。
所以**应用级 401 不是重写后端的职责**：重写必须与厂商**同样宽松**，否则在"前端直连后端"的 e2e 场景里会 401 掉厂商本来能过的页面。

## 我上一轮做错了什么

第 8 轮我给 `/v1/role` 与 `/v1/service` 都加了 `AuthSupport.requireSession`——**这是过度收紧**：
厂商这两个端点无 cookie 返回 200。若不改，前端在网关之外的场景（我们的 harness、以及未来任何直连）会被我们 401 挡住。
**本轮已撤回**，只在 `/v1/user` 保留应用级 401（与厂商一致）。

## 对门槛的含义

- e2e 比对必须按**同样的边界**做：受保护的是"网关"，不是"应用"。
- 未来真正切流时，`gateway-auth` 原样保留即可继续保护业务路径（见 `12-auth-contract.md`）。
- 顺带验证：撤回后我们的 `/v1/role`、`/v1/user/get`、`/settings`、`/v1/mode/current` 全部与厂商同状态码。
