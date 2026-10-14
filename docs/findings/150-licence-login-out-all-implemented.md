# `GET /v1/licence/loginOutAllUser` 规格 + 实现（第 157 轮）

**证据等级**：〔代码事实〕（厂商 `LicenceController:108-113` + `LicenceService.loginOutAllUser:1146-1149` +
`RedisService.deleteAllRedisKey:53-57` 逐行读）+〔运行实测〕（**通过我方自己的在线用户接口做端到端验证**）。2026-10-14。

## 1. 端点

```java
@GetMapping("/loginOutAllUser")
public Response<Boolean> loginOutAllUser()
```
用途：试用期到期时把所有在线用户踢回登录页。

## 2. 流程（很短，但有一处**不是 FLUSHALL**）

```
redisService.deleteAllRedisKey():
    redis.keys("gateway-token:*") 逐个 del
    redis.keys("login-list:*")    逐个 del
return true
```

**照抄点**：**只删这两类键**（`gateway-token:*` 会话键、`login-list:*` 会话列表键），
**不是 `FLUSHALL`**——其它命名空间的键必须留着。

## 3. 实现与验证

**实现**：`IUserSessionRepository.deleteAllSessions()`（会话端口的自然归属，因为它本来就管这两个键空间）
+ 基础设施实现（`scan(prefix + ":*")` 后逐个删）；新增 `ILicenceCommandService` + 实现
（`loginOutAllUsers()` → 调会话端口，恒 true）；`LicenceController` 加 `GET /v1/licence/loginOutAllUser`。

### 3.1 离线：3 例，全量 **564** 条 0 失败

- `LicenceLoginOutAllTest`（domain，1 例）：调用端口并返回 true；
- `UserSessionDeleteAllTest`（infrastructure，2 例）：**只**扫 `gateway-token:*` 与 `login-list:*`
  并逐个删（断言**没有**扫其它前缀）、空结果不报错。

### 3.2 在线：**端到端**（借我方已有的在线用户接口当"观察窗"）

本项目的后端**不做登录校验**（鉴权在 gaop-gateway-auth 那一层），所以"再调一次接口看是否 401"验证不了。
改用 `GET /v1/role/switchRole` 的 `onLineUserList`（它读的正是 Redis 的 `login-list:*`）作为观察窗：

| 步骤 | 请求 | 实测 |
| --- | --- | --- |
| ① | `POST /v1/pub/login {admin/admin123/admin}` | `200`（日志：`登录成功 username:admin 战位:admin ttl:86400s`） |
| ② | `GET /v1/role/switchRole?type=1&page=1&size=10` | `200`；`admin -> onLineUserList: ["admin"]` ⇒ 会话确实在 Redis 里 |
| ③ | `GET /v1/licence/loginOutAllUser` | `200 data:true` |
| ④ | 同 ② 再查 | `200`；`admin -> onLineUserList: **[]**` ⇒ **会话键被清掉了** |

### 3.3 收尾

会话已被本端点自己清空（无残留 Redis 键）、临时 NodePort 与后端进程已回收；本轮不写库。

> 顺带发现（非本轮引入）：`GET /v1/role/switchRole` 的 `type` 是**必填**参数，
> 漏传会走全局 `Throwable` 分支 ⇒ 500 + `code:-1`（与厂商的全局异常语义一致，见 `findings/138` §4）。
> 我第一次探针漏了 `type` 就撞上了这个 500，不是回归。
