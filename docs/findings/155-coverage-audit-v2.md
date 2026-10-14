# 覆盖率审计 v2：更正口径 + 剩余清单（第 158 轮）

审计时间：2026-10-14。工具：`tools/coverage-audit.py`（新增，可复跑）。

## 1. 为什么要重做审计：`docs/findings/141` 的口径漏了一整类路径

141 的口径是 `grep -rho "/v1/..." build/gaop-web-ctx/html/`。这个 grep **只看得见字面量**，
而厂商前端有 59 处用 `.concat()` 拼路径，例如：

```js
{url:"/v1/software/".concat(e.id,"/start"), method:"get"}
{url:"/v1/software/".concat(e.softwareId,"/service/").concat(e.serviceName), method:"get"}
```

grep 只截到 `/v1/software/`，**后半段 `/start`、`/service/{name}` 全部丢失**。

后果（141 §3 的结论因此是错的）：

| 141 的说法 | 实际 |
| --- | --- |
| 「软件域**真实缺口只有 4 条**」 | **14 条**：`{id}/start`、`{id}/stop`、`{id}/status`、`{id}/licence`(PUT/DELETE)、`{id}/relate_service`(POST/DELETE)、`{id}/service_entry`、`{id}/updateRemoteCs`、`/backup`、`/import`、`/v2/backup`、`/top`、`/reset_service`、`/order`、`/relationService` |
| 前端调用面 215 条 | 241→240 条（补上 `.concat` 拼出来的 26 条） |

`/v1/software/{id}/start|stop|status` 是**软件域核心生命周期端点**，此前被 141 判为「不存在」，属于会让人
「以为做完了」的假阴性 —— 这正是 141 自己警告过的那种错误。

## 2. 新工具的三条口径

`tools/coverage-audit.py`：

| 口径 | 来源 | 说明 |
| --- | --- | --- |
| **FE** | 厂商原样前端 bundle | 字面量 + `.concat()` 链拼接，归一化后去重 |
| **VENDOR** | `recovered/gaop-be/.../controller/**` | 厂商后端真实 API 面（**重构总量**的真身） |
| **OURS** | `paas-core-trigger/.../http/*Controller.java` | 类级 `@RequestMapping` 前缀 + 方法映射 |

归一化（两侧一致）：去 query/fragment、`//`→`/`、去尾斜杠、`{x}`/`${x}`/`:x` → `X`、连续 `X` 折叠。

**解析要点（踩过的坑）**：本仓库同时存在简写 `@GetMapping` 与全限定
`@org.springframework.web.bind.annotation.PutMapping`，正则必须容忍包名前缀；
Spring 语义是**类级前缀与方法值总是拼接**（不能因为方法值以 `/` 开头就当成绝对路径，否则
`ConfigMapController` 的 `/v1/config_map/backup` 会被解析成 `/backup`）。

## 3. 总账（第 158 轮，109 个已实现路径）

> **⚠️ 数字已被 `findings/156` §6 再次修正**：本文的归一化把"连续 `/X`"折叠成一个键，
> 导致 `/v1/role/{roleId}/{userId}`（DELETE）与 `/v1/role/{id}`（GET）被合并、缺口被吃掉。
> 去掉折叠后的当期口径是 **前端面 241 / 已实现 113 / 剩余 145**（`findings/156`）。下表保留当时的快照。

| 指标 | 数值 |
| --- | --- |
| 前端调用面（FE，归一化） | **240** |
| 厂商后端 `/v1` 面（VENDOR） | **339**（全部 431，含非 `/v1` 的 webhook/registry/websocket） |
| paas-core 已实现（OURS，归一化去重） | **109**（原始映射 122 条） |
| **FE 未实现** | **148** |
| FE 已覆盖 | 92 |
| OURS 有、FE 未见（内部端点或口径变体） | 17 |

## 4. 剩余 148 条 · 按簇

| 簇 | 条数 | 性质（依据既有 findings） | 建议 |
| --- | --- | --- | --- |
| `/v1/pub` | 49 | 节点/集群运维：增删节点、fdfs、docker 配置、重启/关机、etcd、vip | 逐条筛：读类（`serviceStatus`/`task`/`getNodeAlertSetting`/`vip/interfaces`）可做；`restartServer`/`shutdownServer`/`uninstallNode`/`delete` 破坏性，先定安全边界 |
| `/v1/database` | 16 | 用户/权限/表大小/快照/导出下载 | `users`/`user`/`table_size`/`access_control`/`allService` 纯读可做；`export`/`download`/`initSql/download`/`backup_db_data_sync` 依赖 mysqldump+SSH |
| `/v1/backup` | 15 | 备份/恢复列表、日志、设置、单服务恢复 | `list`/`backupLogs`/`backupSetting`/`fileInfo`/`baseData` 读类可做；`reset`/`singleReset`/`delete` 依赖文件+SSH |
| `/v1/software` | 14 | **软件域核心缺口**（见 §1） | `{id}/status`、`{id}/start`、`{id}/stop` 优先；`backup`/`import`/`v2/backup` 复用已有 Excel/GSON 基建；`top` 需 metrics-server；`reset_service` 会删 Deployment |
| `/v1/image` | 13 | 镜像仓 repositories/tags/upload/download/clean/expose | **外部依赖**：需 Harbor/registry |
| `/v1/service` | 11 | `imageStorage`(SSH)、`logs`(Loki)、`node`/`servicePods`(破坏性)、`patch`(YAML)、`reStartBaseService`(重启 kube-system)、`schedulingNode`(写 Node 污点)、`serviceInfoByNode`、`updateServiceRole`(厂商侧 no-op)、`X`(服务详情)、`patchCommand` | `X`/`serviceInfoByNode` 读类可做；其余按外部依赖/破坏性分类 |
| `/v1/role` | 6 | `{id}/users`、`{id}/suRoles`、`{id}/app/X`、`backup`、`import`、`import/template` | **可做**（已有 user/sub_role Excel 基建） |
| `/v1/cs_software` | 3 | 厂商软件上传/关联 | 需确认是否走第三方接口 |
| `/v1/sub_role` | 3 | `import`、`userAddRole`、`roleAddUser` | **可做**（import 复用本轮的 Excel 基建） |
| `/v1/user` | 3 | `getLoginUser`、`tenx_user`、`update_avatar` | `update_avatar` 厂商是 `POST /v1/user/update_avatar/{userId}`，我们已实现带 id 版；FE 另有裸路径调用，需逐条核对 |
| `/v1/sync` | 2 | `POST /v1/sync`(SSH/rsync)、`GET /v1/sync/database/exist`(远端 HTTP) | 外部依赖 |
| `/v1/white_list` | 2 | 白名单增删查 | 可做（本地表） |
| 单条 11 | 11 | `CreateK8sServices/check`、`config_map/importListen`、`database_record`、`lang/import`、`load/update`、`mail/status`、`mode/updateModeType`、`namespaces`、`persistentvolumes`、`pods`、`thirdPart/getUnreadCount` | `pods`/`persistentvolumes`/`namespaces` 纯 K8s 读**可做**；`config_map/importListen` 会改 ingress Service 端口（破坏性） |

## 5. OURS 有、FE 未见（17 条）

`/internal/health`、`/settings`、`/v1/config_map/{name}`、`/v1/config_map/listenBackup`、
`/v1/pub/login`、`/v1/pub/logout`、`/v1/role/exist_role_name`、`/v1/service/{s}/node_port`、
`/v1/software/cancelAuth`、`/v1/software/software/active`、`/v1/software/softwareCode`、
`/v1/sub_role/{id}`、`/v1/user/{id}/roles`、`/v1/user/access_permission`、`/v1/user/export`、
`/v1/user/list`、`/v1/user/recovery`

两类：① 我们为兼容网关/前端另开的内部端点；② 前端用 query/body 传参、路径口径不同的同义端点。
**这类不能直接算「多做」**，改动契约前需逐条核对（例如 `GET /v1/user/list` 与 `GET /v1/user`）。

## 6. 结论

- 剩余**不是 131 条而是 148 条**（同口径下前端面从 215 修到 240）。
- 剩余的重心仍是 `/v1/pub`（49，占 1/3），其次 `/v1/database`+`/v1/backup`（31，均为 SSH/mysqldump 外部依赖）。
- **可立刻推进且无外部依赖的一批**：`/v1/role`(6)、`/v1/sub_role`(3)、`/v1/software` 的 status/start/stop/licence/relate_service(6)、
  `pods`/`persistentvolumes`/`namespaces`(3)、`/v1/white_list`(2)、`/v1/backup` 读类(5)、`/v1/database` 读类(5) ≈ **30 条**。
- 审计工具已入库，后续每轮结束应重跑 `python3 tools/coverage-audit.py`，避免再出现「假阴性当成做完了」。
