# K2 开口：`GET /v1/software`（软件目录列表）——聚合端点，全分支对拉一致

**证据等级**：〔运行实测〕（对拉厂商，**整体 JSON 相等**）+〔代码事实〕。2026-10-14。

## 1. 结果：**整体 JSON 相等**，且覆盖了每一个分支

本环境 `g_software` 原来是 **0 行**（现有样本 `v1_software.json` 只能锁住空列表）。
本轮临时插了 **3 行探针软件**把各分支撑开，抓完即删：

| 覆盖点 | 探针构造 | 结果 |
| --- | --- | --- |
| 列表项形状 | 3 行软件 | **27 键**（`Software` 23 + `SoftwareMessage` 4），两侧键集合一致 |
| 排序第一级 | 一行 `order_top=1` | 该行排第一（厂商 `order_top DESC` **恒在最前**） |
| 排序第二级 | `order=name&sort=desc` | 两侧顺序一致（`探针应用W/P/A`） |
| `url` 分支 | 一行 `type=CS`、两行 `WEB` | CS → `gaop://cs?...`；WEB → `router_key` |
| `releaseStatus` | 一行有 `g_white_list` 路径 `/<key>/**` | 1 / 0，两侧一致 |
| `web=0` | 无 `g_service_port` 行 | 0 |
| `web=2` | 有 `web_entry=1` 行但 `service_name` 不存在于集群 | 2 |
| `web=1` | `service_name` 改成真实存在的 Deployment（`gaop-web`） | 1 |
| `roleList` | 插入一行 `g_role_software`（app_id 指向软件 id） | 非 null 且逐字段一致；无关联时保持 **null**（不是 `[]`） |
| 空列表 | 删掉全部探针后 | `{"softwareList":[],"total":0}`，与既有样本一致 |

**每一次抓取都做了整体 JSON 比较，全部 `True`**。这意味着这个端点在本轮的构造集上**逐字节等价**。

## 2. 它为什么不是"只读列表"（上一轮量清的结论得到验证）

每个软件行都要现场算四样东西（`SoftwareService.getSoftwareList:483-547`）：

- `releaseStatus` —— 白名单里有 `/<key>/**` 就是 1（`softWareReleaseStatus:2878-2885`）；
- `web` —— 0=没有 `g_service_port` 的 `web_entry=1` 行；2=有行但按 `service_name` 找不到同名 Deployment；
  否则按 Deployment 的 `status` 算（`setServiceStatusBySoftware:564-585`）；
- `url` —— CS 类型拼 `gaop://cs?...`，里面**含远端口令**（`remoteName`/`remotePassword`）与请求头
  `x-forwarded-host`；其它类型用 `router_key`；
- `roleList` —— `g_role_software.app_id = 软件id` 反查 `g_role`，输出 `{id,name,key}`。

**厂商那处可疑逻辑照抄了**：`setServiceStatusBySoftware` 的内层分支先 `setWeb(3)` 又**无条件** `setWeb(1)`，
所以那个 3 永远被覆盖。我用两条用例分别锁住"内层恒为 1"和"`availableReplicas` 为 null 时落到 2/3"。
另外厂商在 `status == null` 时会 NPE（500）；我们按"状态缺失"跳过后给出 2，**记录为收紧**。

## 3. 两处实现细节（都不是顺手的小事）

1. **排序列白名单化**：厂商是把 `order` 直接映射到动态 SQL 的列；我们用 `<choose>` 白名单
   （只允许 `key`/`name`/`createdAt`/`updatedAt`，其余落到 `key`），避免把用户输入拼进 `ORDER BY`。
2. **一次查询 Deployment 列表**：厂商先取一次全量 Deployment 再在内存里按 `service_name` 匹配。
   我们同样先收集本页所有 `service_name`，**一次**调 K8s 端口 —— 避免 N 次调用。

## 4. 架构落点

- 域：`domain/software`（`SoftwareEntity` 23 列 / `SoftwareMessageEntity` = 行 + 4 附加键 / `ISoftwareRepository` /
  `ISoftwareQueryService`）；
- **K8s 依赖通过端口隔离**：`domain/software/adapter/port/IWebEntryStatusPort` ←
  `infrastructure/adapter/port/WebEntryStatusPort`（复用切片 1 已有的 `ServiceRuntimeGateway`）；
- 触发：`SoftwareController`（`GET /v1/software`）+ `SoftwareListAssembler`；
- 契约：`read-paths.json` 新增 `GET /v1/software`（15 条读取路径，证据来自 `p__software.3edb99c9.async.js`
  的 `dataIndex` 与字面量计数）。

**一处刻意不做的**：厂商的 `Software` 里 `remotePassword` 会被拼进 CS 类型的 `url` 里返回给前端 ——
这是厂商的设计（前端据此免密打开 CS 客户端），我们照抄了拼串，**但在 `read-paths.json` 里记了一笔**，
因为这意味着远端口令会出现在浏览器可见的响应里。

## 5. 测试

新增 3 个测试类共 11 条：域服务 6 条（url/releaseStatus/web/roleList/空分支）、
K8s 端口映射 6 条（照抄那两处古怪）、HTTP 层 2 条（27 键 + 排序 + `name`>36 拒绝）。
另有**样本回放**：`v1_software_probe.json`（探针录制，已入 `runtime-samples` 与离线测试资源）。

**全量 `mvn test` 137 条全绿**（上一轮 126）。探针行全部删除，库回到 `software=0 / white_list=0 /
service_port=0 / role_software=0 / user=1 / role=2`。
