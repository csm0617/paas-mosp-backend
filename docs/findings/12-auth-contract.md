# 认证最小闭环的契约（替换 gaop-be 所需的全部细节）

**为什么必须"线级兼容"**：ADR-0001 的目的地是**只替换 gaop-be**，`gaop-gateway-auth` 原样保留，
所以重写后端签发的 token、Redis 键与值必须与它认的完全一致。下面每条都带出处或运行实测。

## 一、登录

入口：`POST /v1/pub/login`（`PublicController.java:571`）与 `POST /v1/user/login`（`UserController.java:245`）**是同一个 handler**。

| 环节 | 事实 | 出处 |
| --- | --- | --- |
| 入参 | `{username, password, role}`；`role` 是**战位 key**（如 `admin`），不是 id | `controller/form/LoginForm.java` |
| 口令校验 | `BCrypt.checkpw(password, g_user.password)` | `service/UserService.java:331` |
| token | `UUID.randomUUID().toString()`——**不透明串，不是 JWT** | `service/UserService.java:353` |
| Cookie | 响应头 `Set-Cookie: gateway-token=<token>; Path=/`（**无 HttpOnly / Secure / Max-Age**） | `controller/UserController.java:250` |
| 会话键 | `SETEX gateway-token:<token> <ttl> "<username>:<战位key>"` | `controller/UserController.java:251-259` |
| 会话列表 | `RPUSH login-list:<username>:<战位key> "gateway-token:<token>"`；若配置了最大登录数，`LPOP` 溢出者并 `DEL` 其会话键 | `service/RedisService.java:99-108` |
| ttl | 读 `g_setting.system.permission.expired_time`（**分钟**）；本集群实测 **1440** | `UserController.java:254` + 实测 |

## 二、每次请求的校验

- cookie `gateway-token` → Redis 键 `gateway-token:<cookie值>`。两侧的 `geneKey` **字节级一致**：
  `String.join(":", "gateway-token", token)`（`UserController.java:122` / `LoginAuthServiceImpl.java:64`）。
- `gaop-gateway-auth`：`EXISTS` → `GET` → 用 `split(":")[1]` 当 roleName（`LoginAuthServiceImpl.java:51-58`）。
- `gaop-be`：`getCurrentUser` 在 cookie 缺失或 Redis 无值时抛 `UnauthorizedException`
  → `{"msg":"未认证","code":401}`（`UserService.java:884-903`、`ResponseExceptionConfig.java:32`）。
- **gaop-be 没有任何拦截器**（全仓无 `addInterceptors`）——401 是各端点自己调 `getCurrentUser` 抛出来的。

## 三、Redis 归属

`gaop-be` 与 `gaop-gateway-auth` 都指向 `gaop-redis:6379`（env `REDIS_HOST`/`REDIS_PORT`），
且**都没有配置 database 段** → 同一个 Redis、**同一个 DB 0**
（`gaop-be/application-k8s.yml:32-34`、`gaop-gateway-auth/application.yml:6-8`）。

## 四、运行时实证（2026-10-14 03:0x，集群 172.16.209.25）

```
登录 → redis-cli --scan:
  gateway-token:4bfb128c-a706-4da5-a72f-84b2f15e80bd
  login-list:admin:admin
GET  gateway-token:<uuid>   → "admin:admin"
TTL  gateway-token:<uuid>   → 86400            (= 1440 分钟)
TYPE login-list:admin:admin → list
g_setting.system.permission.expired_time = 1440   （其 desc 同样是双重编码乱码，见 11 号文档）
```

## 五、重写实现清单（可直接照做）

1. `POST /v1/pub/login` 与 `/v1/user/login`：BCrypt 校验 → 生成 UUID → `SETEX gateway-token:<uuid> <ttl> "<user>:<roleKey>"`
   → `RPUSH login-list:<user>:<roleKey> "gateway-token:<uuid>"` → `Set-Cookie: gateway-token=<uuid>; Path=/`。
2. 需要登录的端点：读 cookie → Redis → 取 `username:roleKey`；缺失返回 401 `{"msg":"未认证","code":401}`。
3. logout：删会话键 + 从 `login-list` 移除。
4. **Redis 必须指向同一个实例与 DB**，否则网关认不出。
5. ttl 从 `g_setting.system.permission.expired_time` 读，别硬编码 30 分钟（默认值只是兜底）。

## 七、实现与验证（2026-10-14 03:1x，集群 172.16.209.25）

重写后端已实现该闭环（`domain/user` + `infrastructure/{dao,redis,adapter/repository}` + `trigger/http/AuthController`），
并**在线跑通五步**：

| # | 验证 | 结果 |
| --- | --- | --- |
| ① | 不带 cookie 访问 `GET /v1/role` | **HTTP 401** + `{"msg":"未认证","code":401,"data":null}`（与厂商同款） |
| ② | `POST /v1/pub/login` | 200；信封键一致、`data` **20/20** 键、内嵌 `role` **16/16** 键、token 是 36 字符 UUID、`style=default`、`password=null` |
| ③ | 同一 Redis 里的会话 | `gateway-token:<我方 token>` = `admin:admin`，`TTL=86400`；且该 token 出现在 `login-list:admin:admin` 列表里 |
| ④ | 带我方 cookie 访问我方 `GET /v1/role` | **200**，`code=0`，`total=2` |
| ⑤ | **拿我方签发的 cookie 去调厂商 gaop-be 的 `/v1/role`** | **200**，`code=0`，`total=2` —— 即**原样保留的网关与原厂商后端都认我们签发的会话** |

第 ⑤ 步是关键：它证明"签发侧替换"在认证边界上是**真正可互操作**的，不是纸面兼容。

离线测试另加 5 条（`AuthServiceImplTest`）：BCrypt 正误口令、UUID token、ttl 由 `g_setting` 驱动（1440 分钟 → 86400 秒）、
响应里 `password` 为 null、会话查询与登出删除。全仓 `mvn test` 现为 **15 条用例全绿**。

## 六、未定论（不猜）

- `userConfig.getMaxLogin()` 的运行值未知 → 决定是否"单点登录"；实现时按 0（不限制）并在配置里留开关。
- 网关是否还有别的校验（如 licence）未在本工单范围内查证（`LicenseCheckConfig` 里 `license.check` 默认 `false`）。
