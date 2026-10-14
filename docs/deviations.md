# 契约偏离清单（对外交付版）

**范围**：`paas-core`（重写版）相对厂商 MSOP v1.3.4（gaop）的**全部已知偏离**。
**生成时间**：2026-10-14，第 60 轮盘点。**配套**：每一条都指向 `docs/findings/` 里的实测证据。

**读法**：
- **A 类**＝厂商自身的缺陷/环境依赖，我们**不复制**（或复制了但明确标注）；
- **B 类**＝我们**主动收紧**（安全性/一致性），成功路径的响应与厂商一致；
- **C 类**＝参考环境导致**无法对拉**，我们按代码实现并标注证据来源。

---

## A. 厂商自身缺陷 / 环境依赖（不可对拉或不该复制）

| # | 位置 | 现象 | 我们的处置 | 证据 |
| --- | --- | --- | --- | --- |
| A1 | `GET /v1/service/{serviceName}` | 厂商自身 **NPE**：`TenxService.responseContainer:604` 只判 `limits != null` 未判键存在；本环境工作负载缺 `resources.limits` ⇒ 500 | 按代码实现，标注证据来源为 `/v1/service` 列表项的 `ServicesItem` 形状 | `findings/51` |
| A2 | `GET /v1/service/event`、`/baseEvent` | 厂商 **500 / code=-1** | 未实现（无参考值） | `findings/52` |
| A3 | `TenxService.existService` | 按 label `system/svcName=<name>` 查；本环境无该标签 ⇒ 恒 false（`servicePods` 等端点因此 400） | 未实现相关端点 | `findings/51` |
| A4 | `GET /v1/service/gaop` | 过滤链最后要求 label `system/appName=gaop`；本环境无 ⇒ 厂商恒空 | **照抄该合取项** ⇒ 两侧都是 0（**能解释的 0**） | `findings/58` |
| A5 | `GET /v1/service/serviceInfoByNode` | 单节点集群且全部 Pod 在 master ⇒ 排除 master 后为空 | 未实现；参考值可解释 | `findings/63` |
| A6 | `getLicenceInfo` 的"时速云时间更早"分支 | `TenxCloudImpl.licences()` 返回对象 `merged` 为 null ⇒ 厂商该行 NPE 被空 catch 吞掉 ⇒ 分支**不可达** | 照抄"用授权工具时间" | `findings/46` |
| A7 | `GET /v1/software` 的 `url` | CS 类型的入口地址里**含远端口令** `remotePassword` | 照抄（前端据此免密打开 CS 客户端），已在 read-paths 记录 | `findings/37` |
| A8 | `POST /v1/user` | 缺 `roleListKey` 时 NPE 发生在**插入之后**，留下半成品用户 | 按空列表处理（见 B1） | `findings/31` 等 |
| A9 | `addSoftWare` | `softwareForm.getRoleListKey().size()` 不判 null ⇒ NPE | 按空列表处理（见 B1） | `findings/38` |
| A10 | `PUT /v1/role/{id}` | 响应 `name:null`（controller 参数传错位） | 不复制（见 B3） | `findings/35` |
| A11 | 任意 PUT 的会话 TTL | 厂商把会话 TTL 从 86400 砍到 600 | 不复制（见 B2） | `findings/33` |
| A12 | `PUT /v1/service/updateServiceRole` | **写入的是死代码**：`annotations.put("gaop/serviceRole", serviceKey)` 之后立刻被 `annotations = ...getAnnotations()` **重新赋值覆盖**，该注解**永远不会被写入**；且 StatefulSet 分支会对 `v1Deployment`（null）调 `getMetadata()` ⇒ NPE | **不实现该"无副作用"语义**；错误路径已对拉确认（400 `service.get.service.failed.not.found`） | `findings/69` 前的勘查（本轮） |

## B. 我们主动收紧（成功路径与厂商一致）

| # | 内容 | 理由 | 证据 |
| --- | --- | --- | --- |
| B1 | `roleListKey`/`roleList` 缺失时按空列表处理 | 厂商会 NPE（A8/A9） | `findings/38` |
| B2 | 会话 TTL 不因 PUT 被砍到 600 | 厂商是缺陷 | `findings/33` |
| B3 | `PUT /v1/role/{id}` 响应带上真实 `name` | 厂商传参错位（A10） | `findings/35` |
| B4 | 用户/软件接口不下发口令哈希与远端口令 | 安全 | `findings/26`、`findings/36` |
| B5 | 软件删除链路包一个事务 | 厂商无事务会半删 | `findings/40` |
| B6 | `g_auth` 无 `default_auth` 行时返回 null（折算未激活） | 厂商 `IndexOutOfBounds` | `findings/44` |
| B7 | `GET /v1/service/gaop` 的排序列白名单化 | 厂商把用户输入拼进 ORDER BY | `findings/37`（软件域同款） |
| B8 | `scale` 失败时保留 fabric8 原始消息 | 厂商 `msg` 为空，清空只丢诊断 | `findings/66` |
| B13 | `/v1/user/import` 覆盖分支里 col3/col4 的字面量 JSON `null` 按"无列表"处理 | 厂商 `subRoleList.addAll(roleRelationList)` 会 **NPE → 500**（新用户分支有判空保护，覆盖分支漏了）。模板与 `/export` 都产不出字面量 `null` ⇒ 前端路径上不可达 | `findings/100` 第 (3) 条、`findings/117` |
| B14 | `PUT /v1/service/patchService` 的 B 分支：`portList` 为空时**先校验再删** | 厂商是**先** `delete g_service_port` **再**让 `insertBatch` 的空 `<foreach>` 拼出非法 SQL 报 400 ⇒ 端口映射被白删（与 B5「厂商无事务会半删」同类）。我们保留 400 与键名，但不做这次破坏性写入 | `findings/118` §3、§6 |
| B15 | `POST /v1/service/{service}/updateVolumes` 的 `hostPathList[].readOnly` 为 null 时按"非只读"处理 | 厂商写的是 `getReadOnly() == 2`（`Integer` **拆箱**）⇒ null 时 **NPE** ⇒ 500 `service.file.mapping.or.mount.failed`。前端必传该字段 ⇒ 照抄等于故意留一个 NPE | `findings/119` §4、§6.4 |
| B16 | `PUT /v1/service/healthCheck` 的 `HealthSetInfo.type` 为 null 时按"不改该探针"处理 | 厂商写的是 `getType() == 0`（`Integer` **拆箱**）⇒ null 时 **NPE** ⇒ 500 `系统错误`。同上 | `findings/121` §4、§6.3 |
| B17 | `GET /v1/software/relationService/{id}`：`g_software_app.app_name` 在 k8s 里找不到对应服务时**跳过该行** | 厂商 `serviceMap.get(appName)` **不判空**，紧接着 `setMappingInfo` **NPE ⇒ 500**；"服务删了但关联没清"在本环境很常见，整页 500 不可接受 | `findings/120` §6.4 |
| B18 | `GET /v1/service/event`：pod 的 `metadata.generateName` 为 null 时**跳过该 pod** | 厂商直接 `generateName.substring(0, len-1)` ⇒ **NPE ⇒ 500**；裸 Pod（非控制器创建）没有 `generateName` | `findings/123` §4 |
| B19 | `GET /v1/service/baseEvent`：事件缺 `lastTimestamp` 时**排到最后**（而不是报错） | 厂商 `Comparator.comparing(getLastTimestamp).reversed()` **没有空值保护** ⇒ 比较器抛 **NPE ⇒ 500**。该端点意图是"最新在前"，空值只是没有时间信息 | `findings/125` §4 |
| B20 | `batch-stop`：`spec.replicas` 为 null 时视为"不是 0"（继续下发 scale） | 厂商 `if (replicas == 0)` 对 `Integer` **拆箱** ⇒ null 时 **NPE ⇒ 500**；API Server 会默认该字段，故实际不可达 | `findings/126` §6 |
| B21 | `update_screenshot`：请求体为 null 时按**空列表**处理（即清空该列） | 厂商 `if (!screenShotList.isEmpty())` ⇒ null 时 **NPE ⇒ 500**；前端正常传数组 | `findings/130` §3 |
| B23 | `config_map/relationService`：**省略**厂商开头那次"列 Node 取 master ip"的调用（该 ip 只用于日志） | 厂商在列 Node 失败时**提前返回空列表**——即一个与响应无关的调用失败会丢弃正确的关联结果。判断为不值得照抄 | `findings/143` §4 |
| B22 | `existConfigmap`：`key` 为 null/空时返回 `false`（**不实现**厂商那个"检查全部软件"的分支） | 厂商写的是 `key != null \|\| key.size() != 0` ⇒ null 时 **NPE ⇒ 500**，且该写法让"不带 key"分支**永不可达**（死代码）；实现它等于替厂商发明一个走不到的行为 | `findings/133` §3 |

## C. 参考环境限制导致无法对拉（按代码实现 + 标注证据来源）

| # | 端点/能力 | 缺什么 | 我们的证据来源 |
| --- | --- | --- | --- |
| C1 | `GET /v1/service/{serviceName}` | 厂商自身 500（A1） | `/v1/service` 列表项的 `ServicesItem` 形状（**已对拉**） |
| B12 | `POST /v1/user/export`（与 `/backup`）导出的第 7 列 | 厂商把 **`password` 口令哈希**直接写进 Excel（`generateExportExcel:1021`） | **照抄**（兼容优先）；若将来脱敏属"主动偏离"，须先记录并评估影响 | `findings/100` |
| B11 | `GET /v1/user` 的 `data.token` | 厂商返回 **null**（只在 `login` 里 `setToken`）；我们填了会话 token | **已修**（`findings/95`）：`currentLogin` 不再设 token；契约比对 **L1+L2 全部通过**，且登录响应仍带 token（已验证未被误伤） | `findings/95` |
| B10 | `GET /v1/user/access_permission` 无 Cookie 时 | 厂商 **NPE → 500 系统错误**（判空写在 `new UserLoginInfo()` 之后，永远不成立）；其**本意**是抛 `user.not.logged.in.or.expired` | 我们实现其本意：400 `user.not.logged.in.or.expired` | `findings/93` |
| B9 | `GET /v1/role/software` 无 Cookie 时 | 厂商抛 `UnauthorizedException`（401），我们返回 `{list:[],total:0}` | 影响低（前端总带 Cookie），但按"任何输入都一致"的门槛**应当改**；下次动 K1 时处理 | `findings/91` |
| C2c | 影像域 `repositories`/`tags`/`existRepositories`/`allProjectImageList` | 数据源是 **Docker Registry API**（`dockerRegistryApiService.getImageListByProject`）；参考环境不可达 | 未实现（依赖地图见 `findings/81`） | `findings/81` |
| C2d | `imageStorage` | 通过 **SSH** 连 `sshConfig.host` 取磁盘信息；参考环境连不上 ⇒ 厂商自己返回 `{free:0,total:0}` | 未实现 | `findings/81` |
| C2b | `GET /v1/image/projectList` 的实现数据源 | 参考环境实际生效的是 `K3sImageServiceImpl`（查库 `g_image_group`），而我第一版照着 `K8sImageServiceImpl`（Harbor）实现 | 待按 K3s 实现重写；Harbor 版保留并标注"K8s 变体、参考环境不生效" | `findings/79` |
| C2 | `imageStorage` | 早期判断为"**Harbor 未接入**"；**`findings/77` 已更正**：Harbor 在集群内是可达的（厂商从中读出了真实项目），只是**本机到不了**（`192.168.1.52:30002` 超时） | `projectList` 已实现（`findings/78`）；`imageStorage` 待做；**在线对拉需要把后端部署进集群** |
| C3 | ~~TenX 侧效应（激活后通知、时速云授权时间）~~ **已撤回（2026-10-14 第 127 轮）** | ~~无 TenX 对端~~ ⇒ **前提不成立**：`TenxCloud` 的唯一实现 `controller/cloud/TenxCloudImpl.java` 是**本地 K8s 适配层**（`clusters()` 硬编码 `CID-4f081ba297ba`；`existService`/`service`/`existApp`/`getDeploymentYaml` 全部 → `TenxService`，后者有 54 处 `k8sApiClientManager` 调用；`server.host.tenx` 只用于拼下发给前端的 `TenxUserToken`）。**全模块唯一的真实远程依赖是 Loki**（见 C7） | `findings/120`（含更正说明） |
| C7 | `POST /v1/service/logs`（服务历史日志） | 厂商走 **Loki**（`TenxCloudImpl:181` 是 `TenxCloudImpl` 里唯一的真实 HTTP：`GET <logMonitoring>/loki/api/v1/query_range?...`） | 参考环境无 Loki ⇒ 未实现（与 C6 同类：需外部系统） | `findings/120` §2 |
| C4 | GIS 在线激活 | 无 GIS 服务 | `checkAndActiveGis` 如实返回 false |
| C5 | `serviceInfoByNode` | 单 master 集群（A5） | 参考值可解释（0） |
| C6 | CS 上传 / 导入导出 | 需文件与远端 SSH | 未实现（边界待定） |

---

## 附：契约比对的既有机制（不是偏离，但相关）

- 读取路径契约：`docs/findings/read-paths.json`（+ `paas-core-trigger/src/test/resources/vendor/read-paths.json` 镜像），
  由 `tools/contract-check.py` 校验两份一致；
- 运行时样本：`docs/findings/runtime-samples/`（含 manifest：路径、字节数、sha256）；
- **顺序天然不稳定的端点**（如 `/v1/service/labels`）契约定在**集合层**，比较方式写在 `findings/47`。
