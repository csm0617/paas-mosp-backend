# `/v1/service` + `/v1/software` 剩余端点（第 166 轮）

本轮补齐 **10 条路径**（`/v1/service` 7 条、`/v1/software` 3 条）。证据等级：〔代码事实〕+〔离线单测〕；
凡依赖外部系统（SSH / Loki / Prometheus / Harbor）的一律标 **类 C：本环境不可验证**，不做在线实验。

## 1. 本轮实现清单

| 端点 | 厂商出处 | 关键照抄点 | 验证 |
| --- | --- | --- | --- |
| `GET /v1/service/{serviceName}` | `ServiceController:1404-1481` | 全部命名空间扫 pod 取 `hostIP`/`lastState.terminated`；`<name>-nodeport` 裁剪成 `{ports,type}`（`targetPort` 只取 int ⇒ 具名端口为 **null**）；`nameUs`/`nameFr` = `i18n.service.<en_US\|fr_FR>.<key>`，**空白回退**注解 `gaop/serviceLabel` | 离线 12 条 |
| `PUT /v1/service/updateServiceRole` | `ServiceController:1483-1531` | **厂商缺陷**：`annotations.put("gaop/serviceRole", …)` 之后立刻把变量重新赋值成工作负载自己的注解 ⇒ 注解非 null 时这个 put 被**丢弃**，端点是静默 no-op | 离线 6 条 |
| `GET /v1/service/imageStorage` | `ServiceController:2588-2595` → `K3sImageServiceImpl:128-171` + `SshUtil.getImageStorage:521-561` | `df -h <mount.path> \| awk 'NR==2 {print $2, $4}'`；`switchSize` **只有 T/G/M 三个独立 if**（无 K/字节分支）；连不上 ⇒ `{0,0}` | 离线 4 条（**类 C**） |
| `POST /v1/service/logs` | `ServiceController:3931-3947` → `TenxCloudImpl.getHistoryLog:159-235` | Loki `query_range` URL 逐字（含反引号正则与 `\| json \| line_format "{{.log}}" \|=`）；`start/end = 毫秒 × 1_000_000`；跨度 > 720h ⇒ 400；三级 `count` 语义不同（行数/容器数/pod 数） | 离线 5 条（**类 C**） |
| `DELETE /v1/service/servicePods` | `ServiceController:2327-2343` | `gracePeriodSeconds = force ? 0 : null`；失败 ⇒ 500 `service.delete.pod.failed` | 离线 4 条（**破坏性**） |
| `PUT /v1/service/schedulingNode` | `ServiceController:3087-3165` + `SshUtil:693-716` | 列节点失败 ⇒ `data:false`；名字不在名单 ⇒ 400；SSH 连不上 ⇒ `data:false`；`type=0/1` 两个 `if` **互相独立**；命令 `kubectl cordon/uncordon` | 离线 8 条（**类 C + 破坏性**） |
| `POST /v1/service/reStartBaseService` | `ServiceController:3313-3375` | 同上的"列节点→找 master→SSH"；命令 `kubectl delete pod <n> -n kube-system --force --grace-period=0`；**不校验** podName | 离线 3 条（**类 C + 破坏性**） |
| `POST /v1/software/{id}/service_entry` | `SoftwareController:492-499` → `SoftwareService:2238-2284` | **四道静默返回闸**（软件不存在/未绑定/无此服务/服务数据为空）；参数 `serviceId` **实际是服务名**；`type=1` 走"该行 true + 该软件其它行全 0 + `router_key=/<key>/`"，`type=0` 走"该行 false + `router_key=null`（**不清其它行**）" | 离线 5 条 |
| `POST /v1/software/reset_service` | `AppService.resetService:539-570` | **删掉命名空间里所有不带 `ingress-lb` 标签的 Deployment**；失败 ⇒ 400 `service.reset.failed`；厂商前段的时速云 App 删除**未做**（无 App 层） | 离线 2 条（**破坏性**） |
| `POST /v1/software/top` | `SoftwareController:759-764` → `SoftwareService:1734-1873` | 6 条榜单 PromQL + 每 pod **6 次补齐**查询（cpu 无条件覆盖，其余 5 项**字段为空才查**）；查不到写字符串 `"0"`；`getCpuNum` 按 pod `hostIP` 匹配节点 InternalIP 取 `allocatable.cpu`，失败默认 **1**；`netout` 的正则**没有** `^` | 离线 4 条（**类 C**） |

## 2. 分层与复用

- **没有新增 `@Service` 构造参数**：`ServiceQueryServiceImpl` 的 30 处 `new ServiceQueryServiceImpl()` 测试全部照旧，
  新依赖（`ISshPort` / `ILokiLogPort` / `IPrometheusMetricPort`）用 `@Resource` 字段注入。
- 新增独立服务 `IServiceOpsService` / `ServiceOpsServiceImpl`（破坏性运维），用 `@RequiredArgsConstructor`，
  自带测试构造。
- 新增端口：`ILokiLogPort`（infra `LokiLogPort`，JDK `HttpClient`）、`IPrometheusMetricPort`
  （infra `PrometheusMetricPort`）；`IServiceRuntimePort` 扩了
  `lookupServiceDetail` / `readWorkloadTemplateAnnotations` / `patch{Deployment,StatefulSet}TemplateMetadata`
  / `listNodes` / `deletePod` / `listDeploymentLabels` / `deleteDeployment` / `readPodHostIp`
  / `readNodeAllocatableCpuByInternalIp`。
- `ISshPort` 新增 `execOnHost(host, command)`（厂商"SSH 到节点跑 kubectl"的等价物）；
  原 `exec(command)` 行为不变。
- **domain 仍然不引 Jackson / fabric8 / jsch**：Loki 与 Prometheus 的 JSON 解析在 infrastructure，
  `HistoryLog` 形状的装配在 trigger（`LokiLogAssembler`）。
- 没有改 `pom.xml` / `application.yml`；配置一律 `@Value("${…:<厂商默认值>}")`
  （`prometheus.log-monitoring`、`prometheus.system-monitoring`、`mount.path`、`node.label`）。

## 3. 离线验证

| 层 | 结果 |
| --- | --- |
| `paas-core-domain` software 包全量 | **293 条全绿**（本轮新增 40 条） |
| `paas-core-trigger` assembler 全量 | **49 条全绿**（本轮新增 10 条：`LokiLogAssemblerTest` 5 + `ServiceDetailAssemblerTest` 5） |

新测试文件：
`ServiceDetailAndRoleTest`、`SoftwareServiceEntryAndTopTest`、`ServiceOpsTest`（domain）；
`LokiLogAssemblerTest`、`ServiceDetailAssemblerTest`（trigger）。

**未做在线验证**：本机无 Loki / Prometheus / 可 SSH 节点；`service_entry`/`reset_service`/`top` 属破坏性或外部依赖，
按项目纪律不做在线实验。`GET /v1/service/{serviceName}` 的**形状**有列表项样本作旁证，但**不宣称在线逐字一致**。

## 4. 本轮记录的主动偏离

`docs/deviations.md` B30/B31/B32 三条（见该文件）；均为"厂商此处会 NPE / 越界 / 解析异常 ⇒ 500"的位置，
按厂商本意收紧。另在 C 段登记 C8–C13（类 C 已实现项 + 未做项）。

## 5. 未做项与原因

| 端点 | 原因 |
| --- | --- |
| `PUT /v1/service/patch` | 依赖时速云"App"（`existApp`/`createApp`）与 `AppService.deploymentToYaml`（Deployment→YAML 的 GSON 风格序列化）；本环境是原生 K8s（`findings/79`），照抄需要先补一整套 App/YAML 适配，**本轮不做** |
| `GET /v1/service/serviceInfoByNode` | 依赖 `computerService.getServiceCpuMonitor/getServiceMemoryMonitor`（24 小时窗口的**外部监控后端**）+ 每服务并发补齐；本机不可达，且照抄需要一个线程池与监控端口，**本轮不做** |
| `DELETE /v1/service/node`（删节点） | 破坏性且依赖时速云 `deleteNode` 语义（`ServiceController:2886-2902`）；与 `/v1/pub` 那批"节点运维"同类，**应先定安全边界**（`findings/141` §2 的建议） |
| `POST /v1/software/backup` / `POST /v1/software/v2/backup` / `POST /v1/software/import` | 三个是**独立特性**（导出 `ExportSoftwareRelationService` JSON / 打 zip（含镜像）/ 反向导入含 SQL 与 ConfigMap），体量与"新增一批端点"不同级；本轮只登记，不做 |
| `POST /v1/software/{softwareId}/updateRemoteCs` | multipart 上传 CS 程序包并 SSH/SFTP 到远端节点（`CsSoftwareUploadService`），**类 C 且依赖远端目录约定**；本轮不做 |
| `GET /v1/software/relationService`（**裸路径**，带 `?name=`） | **厂商后端没有这个映射**：`SoftwareController` 只有 `GET /relationService/{id}`；`ConfigMapController` 的 `GET /relationService?name=` 在 `/v1/config_map` 前缀下。即厂商后端对这条前端调用返回 404（口径噪声）。我们**不发明**一个厂商没有的行为 |

## 6. 覆盖率

| 指标 | 上轮 | 本轮 |
| --- | --- | --- |
| 厂商后端 API 面已实现 | 126 | **214**（含并行轮次的其它簇） |
| `/v1/service` 缺口 | 10 | **3**（`node`/`patch`/`serviceInfoByNode`） |
| `/v1/software` 缺口 | 8 | **5**（`updateRemoteCs`/`backup`/`import`/`v2/backup`/`relationService` 裸路径） |

> 注：214 是本轮结束时**整个 trigger 树**的读数（同工作区还有其它 agent 在并行补其它簇），
> 不能全部记在本轮名下；本轮交付的是上面 §1 的 10 条。
