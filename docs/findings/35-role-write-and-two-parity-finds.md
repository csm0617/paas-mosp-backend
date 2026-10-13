# 切片 3b 写路径：新增/修改战位 + 两处对拉才发现的差异

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果

| 端点 | 实测结果 |
| --- | --- |
| `POST /v1/role` | 响应**逐字段相同**（除 id/key/name/时间戳这些必然不同的）；用户关联两侧各写入 1 条 |
| `PUT /v1/role/{id}` | 响应**逐字段相同**；`key` 不带时保持原值 |
| 重名/重 key 新增 | 400（见 §4 的口径说明） |
| 改 `admin` 且带 key | 400 "不能修改运维管理员的标识" ✅ |
| 改成保留字 `admin` | 400 "标识 admin 是保留字，不能修改为该标识" ✅ |
| 改不存在的战位 | 400 "席位不存在" ✅ |

## 2. 对拉发现的两处差异（都已定论）

### 2.1 新建用户的 `updatedAt` 应该是 **null**（**已修**）

我第一版实现给新用户设了 `createdAt` 和 `updatedAt` 都是 now。对拉实测：

```
vendor  POST /v1/user → updatedAt = null    GET /v1/user/{id} → updatedAt = null
mine    POST /v1/user → updatedAt = <now>                        ← 差异
```

根因：厂商 `add` **只设 `createdAt`**（`UserService.java:507-513`），不设 `updatedAt`。
页面「更新时间」列渲染的是 `updatedAt || createdAt`，所以**显示不受影响**，但**线值必须一致**——
已改为 null，并补了单测 `newUserHasNullUpdatedAt`。

**这是我自己引入的偏离**：上一轮做 `POST /v1/user` 对拉时，我把 `createdAt`/`updatedAt` 一起放进了"忽略集合"
（理由是"时间戳无法相同"），结果把"字段该不该有值"这个**结构性问题**也一起忽略了。
**教训：对拉时"值无法相同"的字段要区分两种情况——"值必然不同"（可以忽略）与"该字段是否该存在"（不能忽略）。**

### 2.2 `PUT /v1/role/{id}` 的响应里 `name` 是 **null**（**不复制**）

实测：

```
vendor  PUT /v1/role/{id} → data.name = null      ← 但库里和随后的 GET 都是 "改后V"
mine    PUT /v1/role/{id} → data.name = "改后M"
```

根因是**参数位置传错**（`RoleController.java:249`）：

```java
detail.setName(this.i18nUtil.getAutoNameMessage("role", detail.getKey(), detail.getName()));
//                              ↑ 签名是 (module, language, key)，这里把 key 当 language、name 当 key
```

于是拼出来的设置键是 `i18n.role.<战位key>.<战位名>`，必然查不到 → `getAutoNameMessage` 返回 **null** →
把已经查出来的 `name` 覆盖成 null。**只影响响应，不影响库**（随后 GET 正常）。

**决定：不复制。** 理由是"厂商把一个自己已经知道的字段返回成 null"——前端保存后会显示空席位名，
而刷新一次又好了。按 `docs/findings/30` 立的规矩（厂商缺陷不自动成为契约）记录不实现。
代价：保存后那一瞬间的显示与厂商不同（我们显示正确名字）。

## 3. 保留字保护矩阵（照抄，含两个古怪处）

| 场景 | 结果 |
| --- | --- |
| 目标是 `admin`，**表单带了 `key`**（哪怕与当前 key 相同） | 400 `role.can.not.edit.admin.key` |
| 目标是 `dev`，表单带了 `key` | 400 `role.can.not.edit.dev.key` |
| 表单 `key` 是 `admin`/`dev` | 400 `role.identifier.reserved.word` |
| 目标是 `admin`，表单**不带** `key` | **允许**（只改 name/desc） |
| `cicd`/`root` | **完全不受保护**（与删除路径一致，见 `findings/29` §4） |

两个古怪处也照抄：
- 新增时 key 长度上界写的是 **`< 0`**（不是 `<1`）⇒ **空 key 也能通过**（`RoleService.java:540`）；
- 新增时 `desc` 空白 → **null**，修改时空白 → **空串**（与用户的两个端点同样的模式）。

## 4. 一处"看起来是差异、其实不是"的口径

错误路径探测里，"重名新增"一度显示我们 200、厂商 400。查下来是**探测顺序造成的**：
两个请求用同一个名字、**我们那侧先跑**并把行建出来了，厂商那侧随后看到的就是"已存在"。
把顺序换过来结论就相反。**这说明"同一个探针在两侧顺序执行"本身会污染对照**——
涉及唯一约束的用例，两侧必须用**互不相同的值**（本轮新增战位就是这么做的：`probeRoleV`/`probeRoleM`）。

## 5. 一处仍未复现的测试异常（如实记录）

在改完 `UserCommandServiceImpl`（去掉新建用户的 `updatedAt`）后的一次 `mvn test` 里，
`K1UserInfoContractTest` 的 **7 条全部 404**（上下文正常启动、Tomcat 起了端口，但所有端点都 404）。
随后**连续两次全量 `mvn test` 与单独跑该类都是全绿**，未能复现。
已记录在此：如果再现，优先怀疑**上下文缓存 + 增量编译**的组合，排查方向是看 404 时 DispatcherServlet 的 handler 映射数。

## 6. 实现落点

- 域：`IRoleCommandService.prepareNewRole/prepareEditedRole`（校验 + 构造，含上面全部古怪处）；
- 用例：`IRoleWriteCase.addRole/editRole`（事务内：插行/更新行 + 用户与子角色关系）；
- 装配器：`admin`/`dev`/`adminOrDev` **改为在 `RoleListAssembler` 里按 key 现算**——
  厂商那三个是模型上的计算 getter，**任何响应里都不该是 null**；新建/修改的响应是"重新查出来的行"，
  只靠领域层设置会出现 null（这正是对拉抓到的那条差异）；
- `softwareList`（**软件名**列表）**本轮未处理**：需要 `g_software`，属 K2 域（地图上的欠账）。

**测试 118 → 124 条全绿**（连续两次全量通过）。
