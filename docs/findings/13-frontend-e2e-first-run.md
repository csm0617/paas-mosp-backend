# 真实前端对我们的后端：首次 e2e 实测（2026-10-14 03:15）

## 怎么跑（已落成工具）

```bash
# 1) 起后端（示例：集群 MySQL/Redis；Redis 需临时可达，见文末注意事项）
MYSQL_HOST=... REDIS_HOST=... java -jar paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar
# 2) 起 harness：托管**原样的**厂商前端，把 /api/gaop/** 反代到后端
GAOP_TOKEN=<会话值> DIST=<前端产物目录> python3 tools/frontend-e2e-harness.py 8099
# 3) 用 CDP 驱动本机 Chrome 打开页面并回收证据
GAOP_TOKEN=<会话值> node tools/frontend-e2e-check.js "http://127.0.0.1:8099/gaop/software/workloads" /tmp/e2e-workloads
```

前端产物：`/Users/csm/Desktop/Project/msop/build/gaop-web-ctx/html`（厂商镜像里的原文件）。
**关键点**：它以 `window.routerBase = "/gaop/"` 发布，静态资源都是 `/gaop/...` 绝对路径，
所以 harness 必须把它挂在 `/gaop/` 下（一开始挂在 `/` 导致 JS 根本没加载——那次"页面调用了 `/v1/user`"其实是我自己的 curl）。

## 已成立的

| 观测 | 结果 |
| --- | --- |
| 前端静态托管 + 路由 | 页面**真的渲染了**：`<title>MSOP</title>`、左侧菜单（全局概览/镜像管理/应用管理/网络管理/…）、右上角用户名 **admin** |
| 会话 | 注入 `gateway-token` cookie 后，`GET /api/gaop/v1/user` → **200**，`data.username=admin`（布局据此认为已登录） |
| 控制台 | 出现 `LOGIN/SUCCESS:gateway-token=<uuid>`（前端确认 cookie 里的会话） |

即：**"厂商前端 → 我们后端"这条链路已经通了**，`/v1/user` 是第一个被真实消费的端点。

## 还没成立的，以及原因

访问 `/gaop/software/workloads` 时被重定向到 `/gaop/no-auth`，页面显示 **403 抱歉，您没有权限访问此页面**。

- 路由表里 `/software` 带 `access: "canAdmin"`（`recovered-web/routes.json`），子路由继承 → 需要能判定 `canAdmin`。
- cookie 能证明"已登录"（`/v1/user` 200），但**判定不出 `canAdmin`**：本次没有走登录表单，前端当前会话里没有战位/权限态。
- **根因未定论**：`canAdmin` 的定义与被喂数据在压缩产物里没直接定位到；下一步应改成**驱动真实登录表单**（填用户名/口令/选战位）再进页面，而不是只注入 cookie。

## 更有价值的发现：页面外壳每次都会调这些端点

打开任意页面（含 403 页）时，前端**必定**发出下列请求。标 ✅ 的已实现，其余是我们还没做的：

| 状态 | 端点 | 归属 |
| --- | --- | --- |
| **200** | `GET /v1/user` | ✅ 已实现（本轮） |
| 404 | `GET /settings` | 系统设置（**外壳必需**） |
| 404 | `GET /v1/lang/current` | 国际化（**外壳必需**） |
| 404 | `GET /v1/mode/current` | 作战模式（**外壳必需**） |
| 404 | `GET /v1/licence/info` | 授权许可 |
| 404 | `GET /v1/licence/checkAndActiveGis` | 授权许可 |
| 404 | `GET /v1/pub/quickStart` | 引导 |
| 404 | `GET /ws/info`（SockJS/STOMP 握手，带 `?t=`） | 实时推送 |
| 404 | `GET /cloud/nodes` | 集群节点（另一前缀，可能属别的域） |
| 404 | `GET /v1/user/get?page=1&size=10000000` | 用户列表（**外壳/首页会调**） |
| 404 | `GET /loki/logs/getLogsAssertInfo` | 日志（Loki 代理） |

**结论（对工单 07 的顺序很重要）**：要让"厂商前端一行不改跑通"，**外壳端点必须先行**——
`/settings`、`/v1/lang/current`、`/v1/mode/current`、`/v1/user/get`（可选 `/v1/licence/*`、`/ws/info` 桩）。
它们不属于"软件-服务-端口"这个域，却是任何页面能启动的前置条件。

## 注意事项（踩过的坑）

- **必须挂在 `/gaop/`**：否则 index.html 引用的 `/gaop/umi.*.js` 全部 404，页面是空壳。
- Chrome 的 `--dump-dom` 会被 SPA 的长连接/重试拖住不返回；用 puppeteer-core（本机 Chrome，无需下载 Chromium）驱动才可控。
- 临时把 `gaop-redis` 暴露成 NodePort 才能让本机后端连上会话存储；验证完必须删除该 Service（本轮已删）。


---

## 第二轮实测（2026-10-14 03:25）：登录页也能起来了

补齐外壳端点后重跑，登录页（`/gaop/user/login`）不再报错：

| 端点 | 结果 |
| --- | --- |
| `GET /v1/user/get?page=1&size=10000000` | **200**（本轮新实现；账号下拉的数据源，厂商形状 `{list:[{username,lastLoginRole,avatar}], total}`） |
| `GET /v1/lang/current` | **200**（本轮新实现） |
| `GET /v1/user` | 401（**预期**——未登录，厂商同样 401） |

三张新端点的形状与厂商样本逐字一致：`/settings`（30 条、项键一致）、`/v1/mode/current`（值一致）、`/v1/lang/current`（完全一致）。

**仍然卡住的一步**：登录表单**点不开下拉**。页面文本停在"正在获取授权信息"，
而对应的 `GET /v1/licence/info` 与 `/v1/licence/checkAndActiveGis` 仍是 **404**——
高度怀疑页面在等到授权信息之前处于不可交互状态（未定论，需实现或打桩后复测）。
另外前端还在重启式重试 `GET /api/gaop/ws/info`（SockJS 握手，404），并尝试连 `ws://localhost:9999/`（前端硬编码的 socket 地址）。

**下一步**：实现/打桩 `/v1/licence/info`（语料库里有厂商样本）、`/ws/info`，再复测登录表单；成功后再进 `/software/workloads` 看数据表。
