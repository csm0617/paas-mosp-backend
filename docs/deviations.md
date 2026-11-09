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
| A13 | `GET /v1/pub/serviceStatus` 的 `sysinfo` 键名 | 厂商 `ServiceStatusInfo.sysInfo`（`ServiceStatusInfo.java:18`）只标了 **Gson** 的 `@SerializedName("sysinfo")`，**没有 `@JsonProperty`**；Spring MVC 用 Jackson 序列化 ⇒ 实际输出 `sysInfo`。而厂商前端 `p__SystemOverview` 读的是 `t.data?.sysinfo`（小写）⇒ **永远读不到**、回落默认值 `te.Uw` ⇒ 概览页左列（k8s/dns/logging/monitor/apiserver/etcd）恒显示"正常"——实测 `monitor.status` 明明是 `null`，面板仍是绿的 | 按**前端实际读取的键名**输出 `sysinfo`（小写），让状态真实可见；`appstatus`、`radondbInfo` 照抄 | 2026-11-08 集群实测：`/v1/pub/serviceStatus` 返回 `sysInfo` 而厂商面板全绿；`recovered-web/decompiled/p__SystemOverview.9a060da2.async.js` 读 `data.sysinfo` |
| A14 | `TenxService.allService`（`TenxCloudImpl.allPageService` 之下的公共底座） | 厂商把集群对象 `GSON→Jackson` 二次转换到自带旧模型（`IoK8sApiCoreV1HTTPGetAction.port`、`IoK8sApiCoreV1ServicePort.targetPort` 均为 **`Integer`**）。命名端口（`port: http` / `targetPort: http`）是合法 k8s，却抛 `InvalidFormatException`；异常被 `catch{log.error("转换失败")}` 吞掉 ⇒ 该 `ServicesItem.deployment` 置 null ⇒ `filter(o -> o.getDeployment().getSpec().getReplicas() > 0)` 在 `allService:3179` NPE ⇒ **服务列表、概览、`/v1/pub/service_status` 等 6 个端点整体 500**（`{code:-1,msg:"系统错误"}`） | **不复制**：用 fabric8 原生模型（`IntOrString`）解析，命名端口照常工作；running 计数空值安全（`getReplicas()!=null && >0`）；不把"转换失败"降级成 null 继续算 | `findings/166`（含 javap 逐指令对拉 + gaop-be 日志 + 修复后 `/v1/pub/service_status` 200 实测） |

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
| B24 | `sub_role/update`：① id 不存在 ⇒ **400 `sub.role.storage.not.found`**；② `userList` 为 null 按空列表处理 | ① 厂商先调 `getSuRoleById(id)`，而它是 `selectOne(...).orElse(null)).getKey()` ⇒ id 不存在时 **NPE ⇒ 500**，导致后面那句"不存在就抛 400"**永远不可达**；② `userList.addAll(null)` ⇒ **NPE ⇒ 500**。两者都在正常业务路径上 | `findings/148` §4 |
| B23 | `config_map/relationService`：**省略**厂商开头那次"列 Node 取 master ip"的调用（该 ip 只用于日志） | 厂商在列 Node 失败时**提前返回空列表**——即一个与响应无关的调用失败会丢弃正确的关联结果。判断为不值得照抄 | `findings/143` §4 |
| B22 | `existConfigmap`：`key` 为 null/空时返回 `false`（**不实现**厂商那个"检查全部软件"的分支） | 厂商写的是 `key != null \|\| key.size() != 0` ⇒ null 时 **NPE ⇒ 500**，且该写法让"不带 key"分支**永不可达**（死代码）；实现它等于替厂商发明一个走不到的行为 | `findings/133` §3 |
| B25 | `DELETE /v1/role/{roleId}/{userId}` 的 userId 不存在 ⇒ **400 `user.id.not.found`** | 厂商 `userMapper.selectByPrimaryKey(userId).get()` ⇒ `NoSuchElementException` ⇒ **500**（与 B24① 同类：厂商的"查不到"分支写成了未捕获异常） | `findings/156` §5 |
| B26 | 同一端点上把 `root` 用户从战位移除 ⇒ **正常删关联** | 厂商 `getUserLoginStatusList:1360` 先 `filter(u -> !"root".equals(username))`，再把用户名列表逐个判存在 ⇒ `root` 得到**空列表** ⇒ `userStatusInfos.get(0)` 抛 `IndexOutOfBoundsException` ⇒ **500**。即厂商"不允许删 root 关联"是靠崩而不是靠校验 | `findings/156` §5 |
| B27 | 子角色域写"用户-子角色"关联时**不覆盖用户的 `created_at`** | 厂商 `SubRoleServiceImpl.usersRelateRole:144-160` 每插一条都会 `users.setCreatedAt(now); userMapper.updateByPrimaryKey(users)`（**写错字段**，本意大概是 updated_at）。role 域照抄了这个覆盖（`findings/156` 实测确认），sub_role 域**统一不做**——导入路径与既有 `addSubRole` 保持一致，避免同一域两条路径行为不同 | `findings/158` §4 |
| B28 | 子角色导入的"子角色-战位"关联：**按意图删 `g_sub_role_role`** | 厂商 `SubRoleServiceImpl.subRoleRelateRoles:410-412` 的 delete 写错了表——`userSubRoleMapper.delete(where sub_role_id = ?)`，删的是 `g_user_sub_role`，会把导入上一步刚写好的用户关联清掉。响应恒为 `data:null`，照抄只会静默毁数据 | `findings/158` §4 |
| B29 | 导入 Excel 的关联列填 `[]`（空 JSON 数组）时**按"空列表 = 不动"处理** | 厂商的 `isInWhenPresent(空集合)` 会**去掉整个 where** ⇒ 退化成"全表"：子角色导入的 `[]` 表示**关联所有用户/所有战位**（`SubRoleServiceImpl:598-612`）；战位导入的子角色列 `[]` 则**清空该战位的全部子角色关联**（`RoleService:997-1000`）。前端导出对空列表写的是**空单元格**（不是 `[]`），所以这条路径上产不出 `[]`；照抄等于给手改文件留一个"清库"开关 | `findings/158` §4 |
| B30 | `GET /v1/service/{serviceName}`：pod 的 `containers` 为空时**跳过该 pod** | 厂商 `getServiceDetail:1419-1425` 让 `name` 保持 null，紧接着 `if (!name.equals(serviceName))` ⇒ **NPE ⇒ 500**。空容器列表在 K8s 里可发生（如刚创建/被驱逐的 pod） | `findings/161` §4 |
| B31 | `GET /v1/service/imageStorage`：`df` 输出不足两列时**返回 `{0,0}`** | 厂商 `SshUtil.getImageStorage:527-529` 直接取 `split[0]`/`split[1]`，`df` 报错时输出只有一段 ⇒ **ArrayIndexOutOfBounds ⇒ 500**；厂商本意就是"拿不到就给 0"（连接失败分支就是这么写的） | `findings/161` §4 |
| B33 | 前端「告警记录」查询**要求时间范围必填**且起止合法 | SRS FR-M10-12 验收要点明确要求；厂商实现可以三个条件全空直接查（等于把整张告警记录表拉回前端）。我们保留必填 + `start > end` 提示，**只在非法输入时**与厂商表现不同，成功路径（带时间范围查询）响应一致 | 2026-11-09 实测：厂商 `p__ResourceAlertRecord` 的 `立即查询` 直接 `form.submit()`，无必填校验；SRS `03-功能需求.md` FR-M10-12「验收要点」第 1 条 |
| B32 | `POST /v1/service/logs`：Loki 返回**非 200** 时按"空日志"处理 | 厂商 OkHttp 版不看状态码，直接 `mapper.readTree(body)`；body 不是 JSON（例如 Loki 的 404 纯文本）⇒ **JsonProcessingException ⇒ 500**。控制器本来就会把"日志服务没给出可用响应"翻成 `data:null` | `findings/161` §4 |

## C. 参考环境限制导致无法对拉（按代码实现 + 标注证据来源）

| # | 端点/能力 | 缺什么 | 我们的证据来源 |
| --- | --- | --- | --- |
| C1 | `GET /v1/service/{serviceName}` | 厂商自身 500（A1） | **已实现**（`findings/161`）：形状按 `ServicesItem` 源码 + 列表项已对拉形状，**只做离线单测**（不宣称在线一致） | `findings/161` §1 |
| C8 | `GET /v1/service/imageStorage` | 走 **SSH** 到 `ssh.host` 跑 `df -h`；本机无节点可连 | **已实现**（k3s 分支，`findings/161` §2）；连接失败 ⇒ `{0,0}`（厂商行为）。Harbor（k8s）分支未做 |
| C9 | `POST /v1/service/logs` | 走 **Loki** `query_range`；本机无 Loki | **已实现**（`findings/161` §2）；URL 逐字照抄厂商，**未在线验证** |
| C10 | `POST /v1/software/top` | 走 **Prometheus** `api/v1/query`（榜单 + 每 pod 补齐 6 次）；本机无 Prometheus | **已实现**（`findings/161` §3）；PromQL 逐字照抄，**未在线验证** |
| C11 | `PUT /v1/service/schedulingNode` / `POST /v1/service/reStartBaseService` | SSH 到 master 跑 `kubectl cordon/uncordon`、`kubectl delete pod -n kube-system`；本机无节点可连，且**破坏性** | **已实现**（`findings/161` §2）；只做离线单测（命令字符串 + 分支），**不做在线实验** |
| C12 | `PUT /v1/service/patch` | 依赖时速云"App"概念（`existApp`/`createApp`）与 `AppService.deploymentToYaml`（GSON 风格的 Deployment→YAML）；本环境是原生 K8s，无 App 层 | **未实现**（`findings/161` §5） | `findings/79` |
| C13 | `GET /v1/service/serviceInfoByNode` | 依赖 `computerService.getServiceCpuMonitor/getServiceMemoryMonitor`（24 小时窗口的**外部监控后端**） | **未实现**（`findings/161` §5） |
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
