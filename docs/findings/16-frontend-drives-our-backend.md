# 真实前端已经把我们的后端当后端用了（2026-10-14 03:45）

## 已成立：前端的所有外壳请求都落在我们的实现上，且全部 200

把 harness 的会话注入打开（`GAOP_TOKEN=<我们自己 /v1/pub/login 拿到的 token>`），用 CDP 打开
`/gaop/software/workloads`，harness 侧记录到的代理调用（**去掉 SockJS 噪声**）：

| 状态 | 端点 | 次数 |
| --- | --- | --- |
| 200 | `GET /v1/user` | 6 |
| 200 | `GET /v1/user/get` | 68 |
| 200 | `GET /v1/role` | 15 |
| 200 | `GET /settings` | 2 |
| 200 | `GET /v1/lang/current` | 52 |
| 200 | `GET /v1/licence/info` | 52 |
| 200 | `GET /v1/mode/current` | 1 |
| 200 | `POST /v1/user/login` | 9 |
| 401 | `GET /v1/user`（无会话的那些轮次） | 51 |
| **404** | `GET /v1/licence/checkAndActiveGis` | 51 |
| **404** | `GET /v1/pub/quickStart` | 1 |

**结论**：厂商前端（原样产物）在登录页、外壳取数、会话校验这几条链路上，已经**完全在使用我们重写的后端**。
`/v1/user` 200 与 `/v1/user` 401 的对照也说明我们的会话校验是对的。

## 仍未成立：`/software/workloads` 会被路由守卫弹回登录页

即使 `/v1/user` 返回 200（有会话），前端仍把 URL 重定向到 `/gaop/user/login`。
本轮观察到的两个可疑 404 是：

- `GET /v1/licence/checkAndActiveGis`（登录页与页面启动都在调）
- `GET /v1/pub/quickStart`（进入 loads 页时被调）

**根因未定论**：没有证据表明就是它们导致的；下一轮的做法是先补这两个端点（都可从厂商拿到真实响应），再复测，
若仍弹回则继续按"前端还会调什么"逐条排除。

## 两个工具层的坑（已解）

1. **前端自己的登录请求不会把 `Set-Cookie` 落进浏览器**。我们的 harness 确实透传了该头
   （`curl` 经 harness 能看到 `Set-Cookie`）；同在页面里用 `fetch(..., {credentials:'include'})` 打同一端点，
   cookie **能**存下来（`document.cookie` 可见）。说明是前端 axios 请求的凭据模式所致。
   绕过方式：由 harness 在**服务端**注入 cookie（`GAOP_TOKEN`，本轮的用法），或用 puppeteer 的
   `page.setCookie({..., url})` —— 注意 **IP 主机名必须用 `url` 而不是 `domain`**，否则写不进去。
2. **`networkidle2` 在这个页面上永不满足**：SockJS 会持续重试 `/ws/<server>/<session>/{websocket,eventsource,jsonp,htmlfile}`
   （全部 404），导致 `page.goto` 挂住、Chrome dump 不返回。改用 `domcontentloaded` + 固定等待即可。
   要让日志干净，需要一个真正的 SockJS 传输端点（或让前端不重试）。
