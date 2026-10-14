# 软件授权 + 解除关联服务（第 163 轮）

本批 3 个端点（`/v1/software` 簇）：

| 端点 | 厂商出处 | 关键行为 |
| --- | --- | --- |
| `PUT /v1/software/{id}/licence` | `SoftwareController.activeSoftware:683` → `SoftwareService.activeSoftware:3140-3155` | 软件不存在 ⇒ 400 `software.not.exist`；`softwareCode = machineCodeForSoftware() + Base64(software.key)`；`checkSoftwareActiveCode` 通过 ⇒ **只写 `g_software.active_code`**；不通过 ⇒ 400 `licence.activation.code.error`；响应是**软件详情**（不是 LicenceInfo） |
| `DELETE /v1/software/{id}/licence` | `SoftwareService.resetSoftwareLicence:3157-3159` | `UPDATE g_software SET active_code = NULL WHERE id = ?`——**只清这一列**（与 `cancelAuth` 的三列清空不同） |
| `DELETE /v1/software/{id}/relate_service` | `SoftwareController.unRelateServices:447` → `SoftwareService.unRelateServices:2101-2115` | 入参字段叫 `serviceIdList`，实际拿去跟 **`g_software_app.app_name`** 比；软件不存在或交集为空 ⇒ **直接返回不报错**；删 `g_software_app` + `g_service_port`，通知网关 |

## 1. 复用与新增

- **授权校验完全复用**既有 `ILicenceQueryService.checkSoftwareActiveCode` / `machineCodeForSoftware`
  （上一批就已实现，本批只是把两个端点接上）；新增 `ISoftwareRepository.updateActiveCode`
  （单列写，`ISoftwareDao.updateActiveCode`）。
- `unRelateServices` 新增 `ISoftwareDao.deleteSoftwareAppsByNames` / `deleteServicePortsByNames`
  （`<script>` + `<foreach>` 的 IN 删除），并给 `SoftwareCommandServiceImpl` 注入 `IGatewayRefreshPort`。
- `RelateServicesDTO`（厂商 `RelateServices`，字段名就是 **`list`**）。

## 2. 离线与在线

- 全量 **602** 测试 0 失败（新增依赖后同步更新了 4 个 `SoftwareCommandServiceImpl` 测试的构造调用）。
- 在线（真集群 + 真 MySQL，探针后已还原）：

| 例 | 实测 |
| --- | --- |
| `DELETE /v1/software/probe-sw-2/relate_service` `{"list":["probe-svc"]}` | `200`；`g_software_app`/`g_service_port` 里该软件的行**都被删掉**；响应是软件详情（`status:"STOP"`、`framework:"BS"`） |
| `DELETE /v1/software/probe-sw-2/licence` | `200`；`g_software.active_code` 由 `OLDCODE` → **NULL** |
| `PUT /v1/software/probe-sw-2/licence` `{"activeCode":"garbage"}` | `400` `licence.activation.code.error`（`{"msg":"激活失败，激活码存在错误…"}`） |
| `PUT /v1/software/nope/licence` | `400` `software.not.exist`（`{"msg":"软件不存在"}`） |

**未能在线验证的一处**：授权**成功**分支（写入 `active_code` 并返回详情）需要一份用
`g_auth_info['default_auth'].public_key` 对应私钥签出的 JWT，本环境没有该私钥 ⇒ 只能靠离线逻辑 +
失败分支覆盖（已在 `ISoftwareCommandService.activateLicence` 的 javadoc 里标注）。

探针收尾：探针软件/关联/端口行全部删除回基线；临时 `gaop-redis-tmp` 删除、后端停止。

## 3. 覆盖率

| 指标 | 上轮 | 本轮 |
| --- | --- | --- |
| 前端调用面 | 232 | 232 |
| paas-core 已实现 | 121 | **123** |
| 剩余未实现 | 129 | **127** |

（3 个端点只新增 2 条路径：`relate_service` 的 POST/DELETE 共用同一路径，POST 还没做。）

## 4. 剩余 127 条里"读规格就能判定"的下一批

按"先写代码、最后批量验证"的节奏继续，下一批优先级：

1. `/v1/software/{id}/service_entry`（`setServiceEntry:2xxx`）——需要 `tenxApi.existService/service`
   两个**本地 K8s 适配层**调用，可做；
2. `/v1/software/{id}/status`——非 CS 分支 = 服务状态（`getServiceBySoftware`）；**CS 分支走 SSH**
   ⇒ 与 `/v1/service/logs`(Loki) 同类，记类 C；
3. `/v1/software/backup` / `/v1/software/import`——`exportSoftwareForms` 的 GSON 聚合（含 Deployment/Service
   列表、白名单、i18n 服务名映射），体量较大但无外部依赖；
4. `/v1/cs_software` 三端点——`upload`/`delete` 走 SSH 与文件系统（类 C），`GET /upload` 是纯读；
5. `/v1/database` 读类（`users`/`table_size`/`access_control`/`allService`）——需要**直连业务 MySQL 的管理连接**，
   是新的数据源（比单表查询重），单独一轮做。
