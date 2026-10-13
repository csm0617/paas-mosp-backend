# A/B 对照（已支持 WebSocket 升级）：我们与厂商**行为一致**；并修掉一处会话契约差异（2026-10-14 04:15）

## 一、新工具：支持 WS 升级的 harness

Python 版 harness 处理不了 `Upgrade`，浏览器只能把 SockJS 降级到 xhr-polling——这会给 A/B 混入传输差异。
新增 `tools/frontend-e2e-harness.mjs`（Node、**零依赖**）：静态托管 + HTTP 反代 + **WS 升级透传**（含 cookie 注入）。
日志里能看到 `{"status":101,"method":"WS","path":"/ws/<server>/<session>/websocket"}`，即**真实 WebSocket 建立成功**。

## 二、A/B 结果（同一份前端产物、同一个 CDP 脚本、都走真实 WS）

| | 对我们的后端 | 对厂商后端 |
| --- | --- | --- |
| URL | 停在 `/gaop/software/workloads` | 停在 `/gaop/software/workloads` |
| 外壳 / 菜单 / 模式 / 用户 / 授权提示 | ✅ | ✅ |
| 页面调用的 API | 全部 **200** | 全部 **200**（同一集合） |
| STOMP（真实 WS 升级） | ✅ | ✅ |
| `.ant-layout-content` 内容区 | **无** | **无** |
| 表格 / 数据行 | 0 / 0 | 0 / 0 |

**结论**：在最好的传输条件下（真实 WS、端点全 200、会话有效），**我们与厂商的表现完全一致**。
"工作负载页内容区为空"既不是传输问题、也不是我们的实现问题——厂商在自己家里也是这样。
就"厂商前端一行不改"这条门槛而言，这是**并列对照式的等价**，不是自说自话。

内容区为空的真正条件仍未定论（候选：headless 环境、引导弹窗状态、页面自身的渲染前置）。

## 三、顺手抓到并修掉的一处**真实契约差异**：`max-login=1`

排查过程中我反复遇到"刚拿到 token、几十秒后就 401"。原因是共享 Redis 上的会话列表被裁剪：

```
login-list:admin:admin 长度 = 1
gateway-token:* 键数 = 1
```

厂商的配置是 `application.yml:125  max-login: ${MAX_LOGIN:1}` —— **默认 1，即同一 (用户,战位) 只允许一个活动会话**；
超出时 `RedisService.saveTokenAsList`（`:99-108`）会 `LPOP` 最早的条目并 **DEL 掉它的会话键**。
所以只要厂商那边（或我们自己）再登录一次，先前那个会话就会被踢掉——这正是我看到的 401。

而我们的实现之前传的是 `maxLogin = 0`（不限制）→ **切换后会变成允许多端同时在线**，行为与厂商不同。
本轮已修：`gaop.auth.max-login` 默认 **1**，`AuthServiceImpl` 把它传给会话仓储；单测断言从 `0L` 改为 `1L`。
（`application.yml` 里也显式暴露成 `MAX_LOGIN` 便于运维覆盖。）

**对 e2e 的直接影响**：两个 harness 不能同时持有同一 (用户,战位) 的有效会话——我改成**顺序**跑 A/B（先我方、再厂商），
并且每次起 harness 前现取 token、先 `curl /v1/user` 验一次。
