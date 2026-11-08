# 网关层反编译完成：四层构成、鉴权链与既有实现的交叉印证

**2026-11-08**。本轮把厂商"网关"也做了源码级还原，产物在
`/Users/csm/Desktop/Project/msop/recovered-gateway/`（证据根，非 git 仓），
复现脚本 `msop/tools/recover-gateway.py`。本文档记录对重写工程有用的结论。

## 一、先纠正一个口径：网关不是单一组件

| 层 | 组件 | 性质 | 处理方式 |
|---|---|---|---|
| A | `gaop-gateway-auth:v1.0.11` | **厂商自研 Java**（Boot 2.3.5 / JDK 8） | CFR 0.152 反编译：61 class → 53 `.java`，0 缺失 0 残缺 |
| B | `ingress-nginx/controller:v1.9.4` | **官方开源**（chart 4.8.3，Apache-2.0） | 只还原 93 个 nginx 配置/模板/Lua 原文；Go 二进制**不反编译** |
| C | `gaop-web` 容器 nginx | 官方 nginx + 厂商自改 `default.conf` | 还原最终态与被覆盖层版本 |
| D | `helm/ingress-nginx/extra/*.yml` | **厂商自研** Ingress 规则 | 6 个 YAML 原文 |

B 的官方身份有硬证据：镜像 label `org.opencontainers.image.source =
github.com/kubernetes/ingress-nginx`、`version = v1.9.4`，
`go version -m` 显示模块路径 `k8s.io/ingress-nginx`、go1.21.3。
反编译一个源码公开的 Go 二进制没有信息增益。

**A 的一致性校验**：`gaop-gateway-auth` 此前已作为 `recovered/` 里 7 个后端服务之一
被反编译过。本轮用独立脚本、独立输出目录重跑，58 个文件（53 Java + 5 资源）
sha256 **全部一致、0 差异** → 后端还原流程对网关服务同样可复现，
`recovered/gaop-gateway-auth` 可直接作为权威版本。

## 二、`/auth` 判定链（代码事实）

`AuthController.auth()` 读 `X-Auth-Request-Redirect`（ingress-nginx 模板 L1164
设为 `$request_uri`）作为 `original_url`，按序短路：

```
.css/.js 后缀 → 200 ; 白名单命中 → 200 ; 未登录(2) → 401（触发 auth-signin）
admin(0) → 200 全放行 ; 已登录(3) 且 /gaop 前缀 → 200
三项校验（软件授权 / 角色前端权限 / g_service_port 总网关）全为 3 → 200
否则 → 403（禁止访问 / 激活码过期 / 未授权 / 权限不足）或 401 error
```

数据源：`gateway-token:<token>` Redis 值 `用户名:门户key`（`split(":")[1]` 取角色名）、
`g_white_list`、`g_software` + `g_role_software` + `g_role`、`g_service_port`。
`license.check` 默认 `false` → 软件授权校验默认直接放行。

**对 ADR-0006 的含义**：external auth 只看 **HTTP 200/401/403**，不看响应体
（模板 L1346–1358）。`Response.failed("403", …)` 里 `"403"` 同时充当业务码与
HTTP 状态码（`Message.num2HttpStatus`）。若将来替换网关，L1 契约要从
"JSON 信封形状"扩展到 **HTTP 状态码**；只对齐 JSON 不够。

## 三、与重写工程既有实现的交叉印证（代码事实）

反编译结论不是纸面推演，两仓源码里已经在按这套契约工作：

| 网关侧事实 | 重写工程里的实现 |
|---|---|
| 登录下发 `Set-Cookie: gateway-token=<uuid>; Path=/`，写 `gateway-token:<token>` | `trigger/http/AuthController.java`、`domain/user/service/impl/AuthServiceImpl.java`、`infrastructure/adapter/repository/UserSessionRepository.java` |
| 每次请求从 cookie 取 `gateway-token` | `trigger/http/AuthSupport.java`（`COOKIE_NAME`）；`paas-web/src/api/client.ts` 的 `credentials: 'include'` |
| 网关暴露 `GET /refresh` | `infrastructure/adapter/port/GatewayRefreshPort.java`：异步 GET `http://${gaop.gateway.host:gaop-gateway-auth}:5000/refresh`；白名单与软件服务端口变更都调它 |
| 前端页面受 `auth-url` 保护 | `paas-web/src/stores/session.ts` 明确"凭据是 cookie `gateway-token`（网关侧校验），前端不持有令牌本体" |

`12-auth-contract.md` 的 2026-10-14 在线实测已证明：用重写后端签发的 cookie
调厂商 `gaop-be` 的 `/v1/role` 返回 200。`14-vendor-auth-is-gateway-side.md`
确认 `gaop-be` 无全局拦截器，真正的门在网关。

**尚未接管**（与 ADR-0001「只替换 gaop-be、网关原样保留」一致）：
`/auth` 五段判定链、动态 Ingress 生成（`AllowServiceImpl.doRefresh`）、
三张表在网关侧的读取。这解释了为什么 `GatewayRefreshPort` 只是"通知"而不自己写规则。

## 四、动态 Ingress 与路由（代码事实，影响 C 类偏离判断）

`AllowServiceImpl.doRefresh()` 列出 `gaop-namespace` 下所有 Ingress，
**跳过 `gaop-` 开头的**（保护静态规则），按 `g_service_port` 生成软件路由：
`rewrite-target: /$2`、path `/<key>(/|$)(.*)`、`ingressClassName: nginx`。
启动异步重试 3 次，`/refresh` 可手动触发。

静态规则里 `gaop-be` 用 `/$1`（一个捕获组），动态规则用 `/$2`（两个捕获组），
都实现"剥 `/api/gaop` 前缀"但编号不可混用。
`/gaop/**` 业务页面被 `gaop-web-auth` 重写为 `/`，交给前端 SPA 的
`try_files … /index.html` 兜底 —— 这是厂商深链刷新不 404 的机制。

**C 类环境不可对拉**：chart 的 `values.yaml` 未随离线包提供（安装时生成），
controller 启动参数与渲染后的 nginx.conf 包内不可验证；
测试集群用的是 **Higress**（`rewrite-target: /$2`）而非 nginx class。
两者是"语义对齐"不是"配置照抄"，契约比对应以最终 URL 与响应为准，见
`recovered-gateway/docs/02-路由矩阵.md` 第四节。

## 五、厂商缺陷（代码事实，供 A 类偏离台账参考，不复制）

1. `auth()` 对 `X-Auth-Request-Redirect` 直接 `.endsWith()`，头缺失时 NPE → 500。
2. `/auth` 静态资源只认 `.css`/`.js`，Ingress 放行列表却宽得多，两者口径不一致。
3. `PermissionServiceImpl` L51 `list == null && list.size() == 0` 会 NPE。
4. 白名单与总网关放行每次请求全表查询、无缓存。
5. `SoftwareLicenseFilter` 机器码读取失败时回落到**硬编码 UUID**，激活码校验可伪造。
6. `Response.failMsg("error")` 返回 401（会跳登录页），与 403 分支语义不一致。

## 六、产物索引

```
msop/tools/recover-gateway.py          一键复现（离线，约 18 秒）
msop/recovered-gateway/
  README.md / manifest.tsv / recovery-report.tsv
  gateway-auth/                       A：53 个 .java + 原始 pom/MANIFEST/资源 + 一致性比对
  ingress-nginx/                      B：93 个配置/模板/Lua 原文 + 镜像元数据 + Go 二进制指纹
  gaop-web-nginx/                     C：最终态 + 被覆盖层
  helm-rules/                         D：6 个 YAML + chart 关键原文
  docs/01-鉴权链路.md                 判定链、Redis 键、动态 Ingress、交叉印证、缺陷
  docs/02-路由矩阵.md                 静态/动态 Ingress 与 rewrite 口径
```

清单 179 条逐文件 sha256，校验通过（0 不匹配、0 重复）。
