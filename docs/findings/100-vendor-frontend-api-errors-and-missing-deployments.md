# 100 · 从厂商前端看：哪些接口在报错，还缺哪些部署

> 取证时间：2026-11-08 18:40–19:00
> 对象：测试集群 `172.16.209.170`，厂商前端 `/gaop/` + 厂商后端 `gaop-be:V1.3.4-20260415-1857-arm64`
> 方法：用 `admin/admin123` 经网关登录拿 `gateway-token`，然后按厂商前端
> `api-catalogue.json` 登记的 **88 个 GET 端点**逐个重放（脚本见本轮交互，结论已落表）。
> 证据等级：【实】= 本次实测响应/日志原文，【码】= 反编译代码事实，【推】= 推断。

## 一、一句话结论

**接口报错分三类：缺 3 个监控/指标组件（metrics-server、Prometheus、Loki）、
缺 4 个外部前提（Harbor、VIP daemon、被管理 MySQL 实例、宿主机 node-recovery.sh），
另有一批是"地址还指着厂商老环境 192.168.1.x"的配置问题。**
厂商后端代码本身没有需要修的缺陷（`/v1/service` 的 500 是已知厂商 NPE，A 类不复制）。

本次重放：**88 个 GET 里 30 个非 2xx（34%），映射到 52 个页面中的 24 个**。

## 二、报错 → 根因 → 缺什么（全部实测）

| # | 接口 | 现象【实】 | 根因 | 缺的组件 |
|---|---|---|---|---|
| 1 | `GET /cloud/summary` | 500，`io.kubernetes.client.openapi.ApiException` 出自 `MetricsServerApi.getNodesAndUsage:47` | k8s metrics API 不存在 | **metrics-server** |
| 2 | `GET /cloud/nodesummary` | 500「无法通过metrics-server获取node资源」 | 同上 | metrics-server |
| 3 | `GET /v1/pub/nodeUsage` | 15s 无响应 / 500 | 同上 | metrics-server |
| 4 | `GET /api/v2/dashboards/cockpit` | 500 NPE `DashboardsService.cockpit:548` | 内部先算 pod 指标再算节点曲线，前者因 1–3 为空 | metrics-server（+Prometheus） |
| 5 | `GET /api/v2/dashboards/etcd` | **挂起 > 60s** | 查 Prometheus 区间数据 | **Prometheus** |
| 6 | `GET /api/v2/dashboards/apiServer` | 同上 | 同上 | Prometheus |
| 7 | `GET /v1/pub/nodeMonitor` | 400「获取集群资源信息失败」 | 同上 | Prometheus |
| 8 | `GET /v1/pub/serviceMonitor` | 500 | 同上 | Prometheus |
| 9 | `GET /loki/logs/getDeleteStatus` | 500，feign → `loki-nodeport.loki:30008` `SocketException: Unexpected end of file` | 无 Loki | **Loki** |
| 10 | `GET /loki/logs/getLogsAssertInfo` | 3s 后异常 | 同上 | Loki |
| 11 | `GET /v1/image/repositories`、`/v1/image/imageList`、`/v1/image/expose`、`/v1/service/imageStorage` | 15–60s 挂起，日志 `连接nodeIp:192.168.1.52失败`（K3sImageServiceImpl） | 走 SSH 到节点执行 docker，且 Harbor 地址 `192.168.1.52:30002` 不可达 | **Harbor + SSH_HOST 配置** |
| 12 | `GET /v1/database`、`/v1/database/user`、`/v1/database/users`、`/v1/database/table_size` | 60s 超时 | third 数据源（被管理 MySQL）`MYSQL_HOST` 未设，回落默认 `192.168.1.171:3306`，Druid `initial-size=5` 阻塞 | **被管理 MySQL 实例 + MYSQL_HOST** |
| 13 | `POST /v1/pub/vip`、`GET /v1/pub/vip/interfaces` | 500，feign `http://192.168.1.52:5556/api/vips` connect timeout | VIP daemon 未部署（`vip.host` 默认取 `SSH_HOST`） | **VIP daemon** |
| 14 | `GET /v1/pub/existNodeName`、`POST /v1/pub/unify/etcd` | 400「文件不存在：/data/paas/node-recovery/node-recovery.sh」 | 宿主机没有该文件 | **node-recovery.sh 落地** |
| 15 | `GET /v1/service`、`GET /v1/service/gaop` | 500 `TenxService.allService` NPE | 厂商缺陷（A 类，不复制） | —— |
| 16 | `POST /v1/software/overView` | 500 厂商 NPE | 同上，已在前端从 `anyError` 摘出 | —— |

参数类 400 不算缺陷（本次用占位参数触发）：`/v1/image/tags`、`/v1/image/tagInfo`、
`/v1/image/image_env`、`/v1/user/detailByUserId`、`/v1/database/initSql/download`、
`/v1/service/serviceInfoByNode`、`/v1/pub/testConnect`。

## 三、受影响的厂商前端页面（24 / 52）

完全不可用（主要接口全挂）：**虚拟IP** `/netmanager/vipmanage`、
**数据库信息** `/database-setting/database-info`、**资源概览** `/ops/status-monitor/resources`、
**系统概览** `/global/system-overview`、**驾驶舱** `/global/cockpit`。

部分不可用：应用服务、容器、服务配置、镜像组、镜像构建、应用、资源统计、资源告警（设置/记录）、
日志告警（设置/记录）、备份还原、服务延迟启动、系统服务详情、应用大厅、数据库用户、自动伸缩策略、基础服务。

## 四、需要部署的清单

| 优先级 | 组件 | 厂商包内出处 | 端口 / 命名空间 | 解决 |
|---|---|---|---|---|
| P0 | **metrics-server** | `amd64/helm/metrics-server-components.yaml` | kube-system | 第 1–4 项 |
| P0 | 配置纠偏：`SSH_HOST` / `HARBOR_HOST` / `SYSTEM_MONITORING` 指向本集群 | —— | —— | 第 11 项（部分） |
| P1 | **Prometheus**（chart `prometheus-23.1.0`）+ cadvisor DS + alertmanager | `amd64/helm/prometheus/`、`prometheus-cadvisor.yml`、`prometheus-nodeport.yaml` | ns `prometheus`，NodePort **30003** / 30007 | 第 5–8 项；需设 `SYSTEM_MONITORING=172.16.209.25:30003` |
| P1 | **Loki**（`loki-2.16.0`）+ Grafana 8.3.5 | `amd64/helm/loki-stack/`、`loki-nodeport.yml` | ns `loki`，NodePort **30008** / 30009 | 第 9–10 项；需设 `LOG_MONITORING=loki-nodeport.loki` |
| P1 | **Harbor** | 不在安装包内（外部前提） | `:30002` | 第 11 项 |
| P1 | **VIP daemon** | 不在安装包内（宿主机二进制） | `:5556` | 第 13 项 |
| P2 | 被管理 MySQL 实例（业务库） | 不在安装包内 | `:3306` | 第 12 项；或显式设 `MYSQL_HOST` |
| P2 | `node-recovery.sh` 放到 `/data/paas/node-recovery/` | `amd64/executable/etcd/node-recovery.sh` | 宿主机文件 | 第 14 项 |
| 按需 | TenX 平台 `tce.system-tce:3080`、GIS `gis-server:8080`、OAuth `:8005`、Kafka | 均不在安装包内 | —— | 当前不阻塞页面 |

## 五、两条环境陷阱（会误导判断）

1. **本集群把老环境地址写死在环境变量里**：`HARBOR_HOST=192.168.1.52:30002`、
   `SSH_HOST=192.168.1.52`、`SYSTEM_MONITORING=192.168.1.55:30003`、`FDFS_TRACKERS=192.168.1.55:22122`。
   这不是"没部署"，是"部署了也不通"——先改配置再谈缺组件。
2. **节点 DNS 是 Clash fake-ip（198.18.0.0/15）**：任何域名都解析成功、TCP 都"连得上"，
   然后在读响应时 `Unexpected end of file`。所以**不能用 `nc`／`/dev/tcp` 判断下游是否部署**，
   只能以内置报错与"对应 namespace/service 是否存在"为准。本次 `gis-server:8080 OK`
   就是假阳性（该服务其实不存在）。

## 六、arm64 约束（部署前必须先解决）

集群是 **arm64** 单节点，而厂商安装包 `amd64/` 只有 amd64 产物；
metrics-server 镜像还写死 `192.168.1.102:1080/system_containers/metrics-server:v0.6.4`（厂商内网仓库）。
Prometheus / Loki / Harbor / metrics-server 的 arm64 镜像需要另备（联网拉取或离线导入），
这是 P0/P1 项的执行前提，而不是安装步骤本身的细节。
