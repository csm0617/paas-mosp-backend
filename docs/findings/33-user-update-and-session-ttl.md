# 切片 3a 写路径：修改用户（`PUT /v1/user/{userId}`）——含一条实测出来的会话 TTL 缺陷

**证据等级**：〔运行实测〕（对拉厂商 + Redis TTL 实测）+〔代码事实〕。2026-10-14。

## 1. 错误路径与厂商**逐字节相同**

| 场景 | HTTP | code | msg |
| --- | --- | --- | --- |
| 普通用户改**别人** | **500** | 500 | 席位操作员不能修改其他用户信息 |
| 改不存在的用户 | 400 | 400 | 用户不存在（`user.not.found`） |
| 无会话 | 401 | 401 | 未认证 |
| 有 admin 战位者改 `admin` 用户 | 400 | 400 | 普通运维管理员席位无法修改admin超级运维管理员席位! |

**第一行仍然是重点**：厂商用 `SystemException` → **HTTP 500**，不是 403（与"新增用户"同一模式）。

## 2. 成功路径：除 `id`/时间戳/`password`/`username`/`uuid` 外**逐字段相同**

两侧各建一个初始状态相同的探针用户，用同一份请求体分别 PUT：

```
key 集合一致: True
✅ avatar ✅ birth ✅ desc ✅ email ✅ lastLoginRole ✅ nickname ✅ phone ✅ status
非忽略字段差异: 无
```

被排除的 5 个字段：`id`（各自随机）、`createdAt`/`updatedAt`（当前毫秒）、`password`（我们刻意置 null）、
`username`（两个探针要改成不同的新名，否则第二个会撞 `user.change.username.exist`）、
`uuid`（各自创建时分配，探针并存所以不同）。

**"哪些字段能改"这条最反直觉，且已在真实库上核对**：

| 表单传的 | 库里的结果（厂商与我们**都一样**） |
| --- | --- |
| `email: hacked@x.com` | **`old@x.com`**（原值） |
| `phone: 13800000000` | **`13900000000`**（原值） |
| `birth: 946771200000` | **null**（原值） |
| `desc: ""` | **`""`**（空串） |
| `status: 0` | `0`（改了） |
| `nickname: 改后昵称` | `改后昵称`（改了） |

原因：厂商在 `UserService.java:716-718` 把 `email`/`birth`/`phone` **强制写回原值**——
**这三个字段根本改不动，表单传了也没用**。照抄，并写进单测锁住。
另外 `desc` 空白时厂商置的是**空串**，而**新增**时置的是 `null`（`:476-478`）——两处不同，也分别锁住。

## 3. 一条实测出来的会话缺陷（**不复制**）

厂商源码里那段 cookie 循环（`UserService.java:683-714`）本意是"改了用户名就把自己的会话重建一下"，
但条件写成了 `if (target.username.equals(sessionName) && result)`，`result` 又是
`existingUsername.equals(formUsername)`。于是**只要表单里的 username 与目标当前用户名不完全相同**
（包括"改的是别人"这个最常见的情况），就会掉进 `else` 分支：

```java
this.redisService.expire(redisKey, 10L * 60);   // 会话 TTL 砍到 10 分钟
```

**实测确认（不是推断）**——登录后读 `gateway-token:<token>` 的 TTL，再发一次 PUT：

```
vendor  登录后 TTL = 86400    改**别人**后 TTL = 600    ← 被砍到 10 分钟
mine    登录后 TTL = 86400    改**别人**后 TTL = 86400
```

**决定：不复制。** 理由：这是"改个昵称就把自己 10 分钟后踢下线"的会话破坏性副作用，
厂商明显没有这个意图（`else` 分支是为"用户名变了"写的）。按 `docs/findings/30` 立的规矩
（**厂商缺陷不自动成为契约**），记录在案、不实现。我们的 PUT **完全不触碰会话**，
会话按原 TTL 自然过期。

**风险说明**：切流期间两套后端共用 Redis，若请求打到厂商后端仍会被砍到 10 分钟——
这是"暂不复制"的代价，需要在切流演练时留意（已记入地图的待办）。

## 4. 关系语义与"新增"相反

| 字段 | 新增（`POST`） | 修改（`PUT`） |
| --- | --- | --- |
| `userListKey` 为 null/空 | 跳过 | **删除**该用户 type=2 的关联 |
| `roleListKey` 为 null/空 | 跳过（厂商这里会 NPE） | **删除**该用户 type=1 的关联 |
| `subRoleListId` 为空 | 跳过 | **删除**该用户全部子角色关联 |

## 5. 一处刻意的实现差异（防 NPE）

厂商在"有 admin 战位"分支里 `userService.getByUsername("admin").getId()` ——**库里没有 admin 用户时会 NPE（500）**。
我们用 `Optional` 跳过该检查（没有 admin 用户时就没有"超管不可改"这条可保护的对象）。这是**行为上的收紧**，
不会让任何正常请求的结果与厂商不同。

## 6. 清理纪律（本轮踩到的）

对拉时我用 PUT 改了 `admin` 的 `nickname`/`desc` 做 TTL 探针，**这两个字段在库里是双重编码的字节**
（`docs/findings/11`）。探针结束后已按样本的原始字节逐字节恢复并核对：

```
nickname  期望 C3A7C2AEC2A1C3A7C290E280A0C3A5E28098CB9C == 实库 ✅
desc      期望 C3A5C2B9C2B3… == 实库 ✅
```

**教训**：拿生产/共享库做探针时，"删掉探针行"不够——**探针改过的既有行也要按字节恢复**。
