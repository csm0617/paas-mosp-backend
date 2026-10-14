# 白名单三端点 + 数据库记录（第 162 轮）

本轮起按"**先把代码写完，最后批量验证**"的方式推进（见 `USER.md` 的工作方式约定）。
本批实现：`/v1/white_list` 三端点（`WhiteListController`）+ `/v1/database_record`（`DatabaseRecordController`）。

## 1. `/v1/white_list`（厂商 `WhiteListController` + `WhiteListService:36-80`）

| 端点 | 规格 |
| --- | --- |
| `GET /list?page&size&keyword` | `path LIKE %keyword%`（keyword 为 null 时**不加条件**）+ `created_at DESC`；响应 `{list:[{id,path,createdAt,updatedAt}],total}` |
| `POST ?path=` | 按**原样 path** 查重，命中 ⇒ 400 `software.path.already.exists`；否则插入 **`path.trim()`** + `created_at=now`；随后**异步**通知网关 `GET http://<gaop.gateway.host>:<port>/refresh`；返回**按原样 path 回查**的那一行 |
| `DELETE ?pathId=1,2` | 逐个 id：**查不到就 400** `white.list.delete.path.not.exist`（带 id 参数）；删完通知网关；恒返回 `true` |

**照抄点**：查重用原样 path、入库写 trim 后的、回查又用原样 path ⇒ **带首尾空格的 path 会返回 `data:null`**
（但行确实插进去了）。实测确认（探针 T6）。
网关通知复用既有 `IGatewayRefreshPort`（异步 + 吞异常 + 3 秒超时）。

## 2. `/v1/database_record`（厂商 `DatabaseRecordController` + `DatabaseRecordService:25-33`）

`GET /v1/database_record?page&size` ⇒ `{list,total}`，`updated_at DESC`；7 键：
`{id,realStoreFilename,execFileName,databaseName,updatedAt,execFaultMessage,status}`。
纯读、无外部依赖。同时新建了 `domain/database` 包（后续 `/v1/database` 的 15 条会落在这里）。

## 3. 离线与在线

- 全量 **602** 测试 0 失败（本批未新增测试：两个簇都是"查库 + 拼装"，按新的批量验证节奏，
  测试与后续簇一起补）；
- 在线（真集群 + 真 MySQL，探针后已还原）：

| 例 | 实测 |
| --- | --- |
| `GET /v1/database_record`（空表 / 有 1 行） | `200` `{"list":[],"total":0}` / 7 键形状正确 |
| `GET /v1/white_list/list`（空 / 带 `keyword=probe`） | `200` `{list:[],total:0}` / 命中 1 行 |
| `POST /v1/white_list?path=/probe/one` | `200` `{id:4,path:"/probe/one",createdAt:…,updatedAt:null}` |
| 同 path 再来一次 | `400` `software.path.already.exists`（`{"msg":"添加的路径已存在"}`） |
| `POST ?path=" /probe/trim "`（首尾空格） | `200` **`data:null`**，库里存的是 `/probe/trim` ⇒ 照抄点实测确认 |
| `DELETE ?pathId=999` | `400` `white.list.delete.path.not.exist`（`{"msg":"删除路径的id: 999 不存在"}`） |
| `DELETE ?pathId=4` | `200` `data:true` |

探针收尾：`g_white_list`、`g_database_record` 清空回基线；临时 `gaop-redis-tmp` 删除、后端停止。

## 4. 覆盖率与剩余

| 指标 | 上轮 | 本轮 |
| --- | --- | --- |
| 前端调用面 | 232 | 232 |
| paas-core 已实现 | 118 | **121** |
| 剩余未实现 | 132 | **129** |

**剩余 129 条的分布与性质**（下一步按这个顺序推进，全部先写代码、最后批量验证）：

| 簇 | 条数 | 性质 |
| --- | --- | --- |
| `/v1/pub` | 47 | 节点/集群运维：读类（`serviceStatus`/`task`/`getNodeAlertSetting`/`vip/interfaces`/`nodeUsage`…）可做；`restartServer`/`shutdownServer`/`uninstallNode`/`delete` 破坏性 |
| `/v1/database` | 16 | 域包已建：`users`/`user`/`table_size`/`access_control`/`allService` 纯读；`export`/`download`/`snapshotStatus` 依赖 mysqldump+SSH |
| `/v1/backup` | 15 | `list`/`backupLogs`/`backupSetting`/`fileInfo`/`baseData` 读类；`reset`/`singleReset`/`delete` 依赖文件+SSH |
| `/v1/image` | 13 | 需镜像仓（Harbor/registry）——**外部依赖**，先做 `imageList`/`all_image`/`project` 这类可查库的 |
| `/v1/software` | 13 | `{id}/licence`(PUT/DELETE)、`{id}/relate_service`(POST/DELETE)、`{id}/service_entry`、`{id}/status`(非 CS 分支) 可做；`start`/`stop`/`updateRemoteCs` 走 SSH；`top` 需 metrics-server |
| `/v1/service` | 10 | `{serviceName}` 详情、`serviceInfoByNode`、`patchCommand` 可做；`logs`(Loki)/`imageStorage`(SSH)/`node`/`servicePods`(破坏性) |
| 其余 | 15 | `cs_software`(2)、`sync`(2，SSH/rsync)、`user`(2)、`mode/updateModeType`、`lang/import`、`load/update`、`CreateK8sServices/check`、`config_map/importListen`、`database_record`(已做) |

**口径噪声（不算真缺口）**：`/v1/mail/status`、`/v1/thirdPart/getUnreadCount`、`/v1/pods`、
`/v1/persistentvolumes` 在厂商 controller 里**都不存在**（全仓 grep 无映射），是前端 bundle 里指向
**别的服务**的路径残片。
