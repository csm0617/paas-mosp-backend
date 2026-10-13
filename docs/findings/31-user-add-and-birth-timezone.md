# 切片 3a 写路径：新增用户（`POST /v1/user`）——含一条只有对拉才会发现的 8 小时时区坑

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 四条错误路径与厂商**逐字节相同**

| 场景 | HTTP | code | msg |
| --- | --- | --- | --- |
| 非管理员战位的调用者新增 | **500** | 500 | 席位操作员无法新增用户 |
| 用户名已存在 | 400 | 400 | 用户名已存在 |
| 口令为空白 | 400 | 400 | 密码不能为空 |
| 口令过短（域规则） | 400 | 400 | 新增的用户密码不合法(长度范围在6~36个字符以内) |

**第一行是重点**：厂商用的是 `SystemException`（`UserController.java:298`）→ **HTTP 500**，不是 403。
四条我们与厂商实测对拉全等。

## 2. 成功响应：除三项外**逐字段相同**

同一份请求体分别打到两个后端（各自建完即删，保证初始状态一致）：

```
key 集合一致: True
✅ avatar / desc / email / lastLoginRole / nickname / phone / status / username / uuid
✅ birth
非忽略字段差异: 无
```

被忽略的三项（都是**有意的**）：

| 字段 | 厂商 | 我们 | 理由 |
| --- | --- | --- | --- |
| `id` | 随机 UUID | 随机 UUID | 无法相同 |
| `createdAt`/`updatedAt` | 当前毫秒 | 当前毫秒 | 无法相同 |
| `password` | **BCrypt 哈希原文** | `null` | 刻意偏离：不下发口令哈希（与 `/v1/user` 同一条 L3 账） |

## 3. 那条 8 小时坑（本轮最有价值的发现）

第一次对拉时 `birth` 是：

```
vendor = "2000-01-01T16:00:00.000Z"
mine   = "2000-01-02T00:00:00.000Z"     ← 差 8 小时
```

**两层原因，都要修**：

1. **Jackson 配置**：厂商 `application.yml:11-13` 设了
   `spring.jackson.time-zone: UTC` + `date-format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`。
   注意那个 `'Z'` 是**字面量**（不是时区占位符），靠 `time-zone: UTC` 兜住正确性。
   我们没配 → 输出成 `...+00:00`。已照抄。
2. **驱动对 DATE 列的时区语义不同**：厂商用 `mysql-connector-java **5.1.48**`，我们用 `mysql-connector-j **8.3.0**`。
   同一个 `g_user.birth`（`date` 列）、同样的 `serverTimezone=UTC`，
   5.1 把它读成"**JVM 默认时区的当天零点**"（TZ=Asia/Shanghai → `2000-01-01T16:00Z`），
   8.x 读成"**UTC 零点**"（`2000-01-02T00:00Z`）。

**修法**（不是猜，是显式建模）：领域层把 `birth` 建模为 **`LocalDate`**（`date` 列本来就该是日期），
进出 JSON 时经 `DateTimes` 按 `ZoneId.systemDefault()` 显式换算：

```java
toDate(LocalDate)   = Date.from(date.atStartOfDay(systemDefault()).toInstant())
toLocalDate(Date)   = date.toInstant().atZone(systemDefault()).toLocalDate()
```

这样不再依赖任何驱动的隐式语义，与厂商在**同一 TZ** 下的表现一致（厂商 pod 设了 `TZ=Asia/Shanghai`，
部署时我们的容器也应一致）。修完对拉：`birth` 两侧都是 `2000-01-01T16:00:00.000Z` ✅。

**顺带纠正**：我们的 JDBC URL 原本写 `serverTimezone=Asia/Shanghai`，已照抄厂商的
`characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=UTC`。

## 4. 领域规则（照抄 `UserService.java:450-513`）

| 规则 | 键 |
| --- | --- |
| 用户名去空格后为空 | `user.username.not.empty` |
| 用户名长度 ∉ [2,36] | `user.username.invalid.length` |
| 口令去空格后长度 ∉ [6,36] | `user.password.invalid.length` |
| email > 36 | `user.document.link.invalid.length` |
| desc > 255 | `user.description.invalid.length` |
| 带 uuid 的用户数 > 256 | `user.data.limit.exceeded` |
| desc 空白（新增） | 置 **null**（注意修改时厂商置的是空串 `""`，两者不同） |

**`uuid` 分配的两个古怪之处也照抄**：库里一个用户都没有时结果是 **-1**（不是 0）；
若 0..254 全被占满，循环走完不设值，结果**仍是 -1**。

**字符黑名单是死代码，照抄不修**：厂商用 `matcher.matches()` 配一个**字符类**，
`matches()` 要求整串匹配 ⇒ 只有"长度为 1 且该字符在黑名单里"才命中，而长度校验已经拒掉了长度 1。
**实际效果：`a@b#c` 这种带特殊字符的用户名能通过**。改成 `find()` 才是"修好"，会改变与厂商的行为一致性，
所以照抄并用单测锁住。

## 5. 关系写入与"半成品用户"缺陷

关系顺序照抄厂商：`userListKey`（角色 type=2）→ `roleListKey`（战位 type=1）→ `subRoleListId`（子角色 id）；
按 key 找不到的战位/角色**跳过**；子角色是"先删该用户全部关联再插入"。

**我们与厂商不同的一处（有意）**：厂商 `add` **没有事务**，请求缺 `roleListKey` 时在 `:545` 抛 NPE，
**而用户行已插入** → 留下半成品用户（`docs/findings/30` §5 实测）。重写版把整个用例放进一个事务
（`UserAddCaseImpl`），缺 `roleListKey` 按空处理，**全程成功且不会留下半成品**。

## 6. 未实现（明确记录）

- `GET /v1/user/{userId}`（厂商 200）与 `GET /v1/user/detailByUserId` 我们**没实现**：
  请求 `/v1/user/<id>` 会落到"路径存在但方法不支持" → `500/code:-1`（不是 404）。
  这两个端点属"前端未调用"清单（21 条），但**用户管理的编辑抽屉用 `/detailByUserId`**，下一轮补。
- `PUT /v1/user/{userId}`（修改）、`POST /v1/user/userToRole`、`POST /v1/user/{userId}/roles` 未实现。
- 厂商 `add` 之后还会推 WebSocket 用户状态（`UserStatus`）；实时通道的语义在切片 1 已实现 `K8sTopicBroadcaster`，
  用户状态推送尚未做（前端未读，见 L3 账）。
