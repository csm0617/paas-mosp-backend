# 错误响应契约（400 / 401 / 500 / -1）与 i18n 消息包

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14 采集于运行中的厂商 `gaop-be`。

## 1. 四种失败路径（实测 + 厂商处理器源码）

厂商处理器：`recovered/gaop-be/.../config/ResponseExceptionConfig.java`（`@RestControllerAdvice`）。

| 触发 | HTTP | 响应体 |
| --- | --- | --- |
| `WrongArgumentException`（参数/前置条件） | **400** | `{"msg":<i18n 文案>,"code":400,"data":{"trace":<堆栈>,"message":<i18n 文案>}}` |
| `SystemException`（系统/权限） | **500** | `{"msg":<i18n 文案>,"code":500,"data":{"trace":…,"message":<i18n 文案>}}` |
| `UnauthorizedException` | **401** | `{"msg":"未认证","code":401}`（`data` 为 null） |
| 其余未捕获异常 | **500** | `{"msg":"系统错误","code":-1,"data":{"trace":…,"message":<异常 message>}}` |

**最后一条有实测样本**：`docs/findings/runtime-samples/v1_lang_detail.json` —— `GET /v1/lang/detail` 缺必填参数 `lang`，
返回 HTTP 500 / `code:-1` / `msg:"系统错误"` / `data.message:"Required String parameter 'lang' is not present"`。
也就是说：**Spring 的参数校验异常被厂商的 `Throwable` 兜底吃掉了，变成了 `-1`，不是 Spring 默认的 400**。

**`data` 的键序是 `trace` 在前、`message` 在后**（样本里就是这个顺序）；`message` 与 `msg` 同值。

## 2. `msg` 是文案，不是错误码（这是最容易做错的一处）

`msg` = `messageSource.getMessage(key, args, locale)`，**取不到时回退成 key 本身**。所以：

- 要复刻 `msg`，必须有**厂商的同一份消息包**——已作为契约数据引入：
  `paas-core-trigger/src/main/resources/i18n/messages{,_en_US,_fr_FR}.properties`（来源与约束见同目录 README）；
- 领域层只抛**键 + 参数**（`WrongArgumentException` / `SystemException`，在 `paas-core-types`），
  解析发生在 Trigger 层（`trigger/i18n/MessageResolver`），与厂商同构；
- 回退行为也必须一致：解析失败**返回键本身**，不抛异常。

## 3. 404 **不套信封**，但 **405 套**（Spring 6 迁移带来的新差异，必须显式还原）

实测（kubectl proxy → gaop-be）：

```
GET /v1/definitely_not_mapped_xyz          → HTTP 404，Spring 默认错误体
{"timestamp":"2026-10-13T20:42:24.359Z","status":404,"error":"Not Found","message":"","path":"/v1/definitely_not_mapped_xyz"}

DELETE /v1/user    → HTTP 500，{"msg":"系统错误","code":-1,"data":{"trace":…,"message":"Request method 'DELETE' not supported"}}
PUT    /v1/user    → 同上
PATCH  /v1/role    → 同上
```

**两者必须分开处理**：

- **404（路径都没匹配上）**：Spring 5.3 的 `DispatcherServlet` 直接返回 404，**不抛异常**，所以躲过了厂商的
  `Throwable` 兜底 → 厂商给 Spring 默认错误体。
- **405/415（路径匹配但方法/类型不对）**：异常是从 `handleNoMatch` **抛出来**的，被厂商的 `Throwable` 兜底吃掉 →
  **HTTP 500 + `code:-1` + `msg:"系统错误"`**，与"未捕获异常"那条路径完全一样。

我们跑在 Spring Boot 3.3.5（Spring 6.1）：这两类都实现了 `ErrorResponse`，**如果统一还原成 Spring 默认体，
405 就会变成 405 —— 与厂商不同**。`GlobalExceptionHandler` 因此**只对 404 类**
（`NoResourceFoundException` / `NoHandlerFoundException`）还原 Spring 默认体，其余一律走 `-1` 信封。

**这一条走过一次弯路**：初版把所有 `ErrorResponse` 都还原成 Spring 默认体，是我在实现删用户时**顺手探测**
`DELETE /v1/user`（少了 id，路径变成集合）才发现的——405 侧我方 405、厂商 500/-1。修正后四条路径对拉全等。

**因此**：任何"顺手加个全局异常兜底"或"统一处理框架异常"的改动，都会在这两个方向上无声破坏兼容。

## 4. 本仓实现与偏离

| 项 | 状态 |
| --- | --- |
| 400 / 401 / 500(500) / 500(-1) 四条路径 | 已按上表实现（`trigger/http/GlobalExceptionHandler.java`） |
| `data.{trace,message}` 键序与同值 | 已实现（`LinkedHashMap` 保序） |
| i18n 消息包与"回退成键" | 已实现（`i18n/messages*` + `MessageResolver`） |
| 404（未映射路径） | 已还原 Spring 默认错误体（**刻意不套信封**） |
| **405/415（方法/类型不匹配）** | 走 `-1` 信封（实测对拉后改正，见第 3 节） |
| `UnauthorizedException` 的 `data:null` | 已实现 |

**未定论**：
- 厂商 `data.trace` 是**真实 Java 堆栈**（含类名/行号）。我们给的是自己的堆栈，类名不同。
  前端不读 `trace`（未在产物中检索到读路径）；若后续证明前端展示它，需要重新评估。
- 厂商 401 的 `msg` 走的是 `user.unauthorized` 的**本地化**文案；我们同样走消息包，但**只验证了中文**。
