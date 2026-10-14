# 前端调用面覆盖率审计 + `GET /v1/pub/nodePortUsed` 实现（第 148 轮）

> **⚠️ 已更正（第 158 轮）**：本文的 `grep` 口径漏掉了厂商前端用 `.concat()` 拼出来的路径
> （59 处，例如 `"/v1/software/".concat(id,"/start")`），因此 **§1 的「215 条」与 §3「软件域真实缺口只有 4 条」
> 都是错的**。请以 `docs/findings/155-coverage-audit-v2.md`（前端面 240 条、剩余 148 条）为准。
> 复跑工具：`python3 tools/coverage-audit.py`。

审计时间：2026-10-14（第 146 轮 goal round）。审计口径：**厂商原样前端 bundle** 里出现的 `/v1/*` 路径
（`grep -rho "/v1/..." build/gaop-web-ctx/html/`，共 **215** 条）与我们 `paas-core-trigger` 里
**类级 `@RequestMapping` 前缀 + 方法映射**拼出的实际路径（归一化 `{var}` → `X`）做差集。

## 1. 覆盖率总账

| 指标 | 数值 |
| --- | --- |
| 前端调用的 `/v1/*` 路径（去重） | **215** |
| 我们已实现（含路径变量归一化） | **84**（含本轮 `nodePortUsed`） |
| 未实现 | **131** |

> 上一轮之前的粗口径（只比方法级映射、忽略类级前缀）会把 `/v1/software/*`、`/v1/role/*` 等整片误判为"未实现"；
> 本表已修正为**拼上类级前缀**后的口径。

## 2. 未实现清单（按前缀分组）

| 前缀 | 条数 | 性质 | 建议 |
| --- | --- | --- | --- |
| `/v1/pub` | 43 | 节点/集群运维（增删节点、监控、fdfs、docker 配置、重启/关机、etcd、vip） | **逐条筛**：读类（`serviceStatus`、`task`、`getNodeAlertSetting`、`vip/interfaces` 等）可做；`restartServer`/`shutdownServer`/`uninstallNode`/`delete` 属破坏性，需先定安全边界 |
| `/v1/backup` | 15 | 备份/恢复（列表、日志、设置、单服务恢复） | 多数依赖 `mysqldump`/文件与 SSH；先做读类（`list`、`backupLogs`、`backupSetting`） |
| `/v1/image` | 13 | 镜像仓（repositories/tags/upload/download/clean/expose） | **外部依赖**：需要镜像仓（Harbor/registry）；`imageList`/`all_image` 也许走本地 |
| `/v1/database` | 12 | 数据库运维（用户、权限、表大小、快照、下载） | 依赖 MySQL 实例与 SSH；`table_size`/`users` 可读类优先 |
| `/v1/service` | 9 | 见 §3 | 敏感/外部依赖为主 |
| `/v1/software` | 8 | 见 §3 | `backup`（GSON 聚合导出）可做；`top` 需 metrics-server |
| `/v1/sub_role` | 6 | 子角色 CRUD + 导入导出 | **可做**（与已实现的 role/import 同款） |
| `/v1/config_map` | 5 | ConfigMap 列表/导入导出/关联服务 | **可做**（纯 K8s ConfigMap） |
| `/v1/role` | 4 | `backup`/`import`/`import/template` | **可做**（已有 import 基建） |
| `/v1/user` | 4 | `login`/`logout`/`getLoginUser`/`tenx_user` | 部分已由 `/v1/pub/authing` 等覆盖，需逐条核对 |
| `/v1/licence` | 3 | `gisStatus`/`loginOutAllUser`/`updateFramework` | **可做** |
| `/v1/sync` | 3 | 数据同步状态/存在性 | **可做**（读类） |
| 其它 | 8 | `white_list`(2)、`CreateK`、`cs_software/upload`、`database_record`、`lang/import`、`load/update`、`mail/status`、`mode/updateModeType`、`persistentvolumes`、`pods`、`thirdPart/getUnreadCount` | 混合；`pods`/`persistentvolumes` 是纯 K8s 读，**可做** |

## 3. 两条纵切上仍未做的端点（原样列出，避免"以为做完了"）

**服务域**（`/v1/service`，9 条）：`imageStorage`（SSH）、`logs`（Loki）、`node`（破坏性）、`patch`（YAML 导入）、
`reStartBaseService`（重启 kube-system）、`schedulingNode`（写 Node 污点）、`serviceInfoByNode`（降级）、
`servicePods`（破坏性）、`updateServiceRole`（厂商侧是 no-op）。

**软件域**（`/v1/software`，8 条）：`backup`、`import`、`reset_service`（**会删 Deployment**）、`top`（需 metrics-server）、
`image_base`（实为 `image_base64`，已实现，属口径误报）、`relationService`（裸路径，已实现带 id 的版本）、
`software/active`（路径口径差异）、`v`（实为 `/v2/backup`）。

⇒ 软件域**真实缺口只有 4 条**：`backup`、`import`、`reset_service`、`top`。

## 4. 本轮顺带实现的端点：`GET /v1/pub/nodePortUsed`

从最大的未实现簇（`/v1/pub`）里挑的第一个**纯读**端点。

### 4.1 规格（厂商 `UserService.getNodePortUsedList:2120-2240`）

```
① 列 Deployment，取带**非空 `ingress-lb` 标签**的标签值 ⇒ ingressNameList
   对每个：读该 Deployment 的 annotations，`httpPort`/`httpsPort` 各产出一条
           `{name="框架内部", port=<注解值>}`（**两条同名**；Integer.valueOf 缺失即抛）
② 对每个 ingressName：`split("-")` 取**第 2 段** ⇒ 读 `tcp-services-<段>` 与 `udp-services-<段>`
   每条 data：`key=nodePort`、`value=<proto>/<serviceName>` ⇒ `{name=serviceName, port=key}`
③ 列 `type=NodePort` 的 Service：name 取标签 `name`，空则取 `app`；
   每个端口 ⇒ `{name = <name> + ":" + <service port>, port = <nodePort>}`
入参 port 非空 ⇒ 只保留 port 相等的条目；③ 读失败 ⇒ 记日志后**直接返回**（保留 ①② 结果）
```

**照抄点**：①②两条**同名"框架内部"**；条目名用 **service port** 而 port 字段是 **nodePort**；
`Integer.valueOf(注解值)` 与 `split("-")[1]` 都不做空值保护（照抄）。

### 4.2 离线：`NodePortUsedTest` 6 例，全量 **499** 条 0 失败

### 4.3 在线：真集群（造 `probe-ing`（`ingress-lb` 标签 + httpPort/httpsPort 注解）与 `probe-np`（NodePort 服务））

| 例 | 实测 |
| --- | --- |
| ① 无过滤 | `200`，4 条：`框架内部 -> 30080`、`框架内部 -> 30443`、`gaop-redis:6379 -> 31224`、`probe-np:8080 -> 32253` |
| ② `?port=32253` | 只剩 `probe-np:8080`（**条目名是 service port 8080，port 是 nodePort 32253**）⇒ 照抄点实测确认 |
| ③ `?port=1` | 0 条 |

（① 里的 `gaop-redis` 是启动后端时临时建的 NodePort 服务，正好充当了"真实数据"的第三方样本。）

### 4.4 收尾

`probe-ing`/`probe-np`/临时 `gaop-redis-tmp` 全部删除、后端进程已回收；本轮不写库。
