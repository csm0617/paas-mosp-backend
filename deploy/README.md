# paas-core 部署包

自研前后端的 **linux/arm64 离线部署包**：构建、打包、部署、切流、回退、卸载。
目标现场是**气隙内网**，所以所有产物自带镜像与 SQL，全程不访问 registry。

> 目标机操作手册在 [`README-OFFLINE.md`](README-OFFLINE.md)（会随包一起分发）。
> 本文是**包的设计与厂商对照**，给维护者看。

## 1. 边界：只替换应用层的两个服务

厂商 MSOP 一套 8 个进程，本包只替换其中两个：

| 厂商对象 | 本包 | 是否替换 |
| --- | --- | --- |
| `gaop-be`（8080，业务 API） | `paas-core` | ✅ 自研实现，66 个端点映射（见 `docs/findings/`） |
| `gaop-web`（80，静态站点） | `paas-web` | ✅ 自研前端，逐值照搬厂商布局（ADR-0004） |
| `gaop-gateway-auth`（5000） | — | ❌ 用厂商镜像 |
| `gaop-auth`（19526） | — | ❌ 用厂商镜像 |
| `gaop-fdfs`（9527） | — | ❌ 用厂商镜像 |
| `gaop-image-uploader`（18080） | — | ❌ 用厂商镜像 |
| `gaop-diagnostic`（55555） | — | ❌ 用厂商镜像 |
| `gaop-startup`（9889） | — | ❌ 用厂商镜像 |
| MySQL / Redis / MinIO / FastDFS / 网关 | — | ❌ 复用现场既有设施 |

理由：自研的验收口径是「厂商前端一行不改 + 契约逐端点对拉」（ADR-0004/0006），
替换面越大，回退面越大。中间件与其余 6 个服务不在本轮范围内。

## 2. 厂商部署脚本梳理（证据根 `Project/msop/artifacts/k8s/`）

厂商交付的是一组**集成验证脚本**，不是安装器。职责与执行顺序如下：

| 文件 | 职责 | 执行时机 |
| --- | --- | --- |
| `deploy-msop.sh` | 编排：`up / schema / seed / status / down` | 入口 |
| `msop-foundation.yaml` | MySQL `ExternalName` 别名 → `gaop-mysql`；Redis Deployment；建库 Job；SA `msop` + cluster-admin | up 第 1 步 |
| `msop-apps.yaml` | 7 个后端 Deployment + Service，全部挂 `sa/msop` | up 第 4 步 |
| `msop-ingress.yaml` | 5 条 Ingress（fdfs 上传、镜像上传/下载、`/api/gaop/**`、diagnostic） | up 第 5 步 |
| `gaop-web-arm64.yaml` | 前端 Namespace + Deployment + Service + Ingress | 独立 apply |
| `sql/db/migration/*.sql` | 87 个迁移（V0.0.1→V0.8.6），灌完 39 张表 | `schema` |
| `seed/msop-seed.sql` | admin 用户 + 运维管理员门户 + 关联 | `seed` |
| `fix-node-clock.sh` | 节点时钟三层修复（RTC / time-wait-sync / kubelet 依赖） | 部署前 |
| `verify-web-arm64.sh` | 前端验收（容器内 uname/nginx/文件数 + 两拓扑 HTTP） | 部署后 |

`up` 的实际链路：复制口令 → foundation → 等 Redis/建库 Job → `kubectl run` 验库 →
**宿主机 `docker run` 灌 87 个 SQL** → apps → ingress → 逐个 `rollout status`（各 5 分钟）。

### 2.1 厂商脚本里必须知道的问题（逐条有出处）

| # | 问题 | 位置 | 后果 |
| --- | --- | --- | --- |
| 1 | 应用账号口令 `gaop123456` 明文写死 | `msop-foundation.yaml` 建库 Job 的 SQL、`deploy-msop.sh:34` | 任何拿到清单的人都知道库口令 |
| 2 | MySQL 地址硬编码集群 IP `10.96.1.127` | `deploy-msop.sh:81,84,91` | 换集群（Service 网段不是 10.96/12）即失败 |
| 3 | 灌库依赖**宿主机 docker** + `--network host` | `deploy-msop.sh:78,90` | 只有 containerd/podman 的节点直接不可用 |
| 4 | 假定 `default/mysql-service`、`minio-service` 已存在 | 文件头注释、`copy_secrets` | 是集成验证脚手架，不是可交付安装包 |
| 5 | `gaop-image-uploader` 挂 `hostPath: /var/run/docker.sock`、`type: Socket` | `msop-apps.yaml:273` | 该节点 socket 实际在 `/run/docker.sock`；`type: Socket` 会挂成空文件（运行实测） |
| 6 | 全部 Pod 不声明 CPU（连 limit 都不写） | `msop-apps.yaml` 文件头 | 只因该节点 2 核 requests 已满；换环境后无资源保证 |
| 7 | 命名空间、ingressClass 硬编码 | 全文件 | 无法部署到第二套环境 |
| 8 | Job 名固定且不可重入 | `msop-create-db` | 重跑要手动 delete，脚本没做 |
| 9 | 无镜像清单/无校验，靠人工 `docker load` | 全包 | 漏 load 一个就是 `ImagePullBackOff` |
| 10 | `load_schema` 把 stderr 丢进 `/dev/null` | `deploy-msop.sh:81` | 失败只看到文件名，看不到原因 |
| 11 | `set -uo pipefail` 但**没有 `-e`** | `deploy-msop.sh:16` | 中间步骤失败会继续往下跑，最后才表现为「某服务没起来」 |
| 12 | `status` 用 `free -m` / `docker stats` 读**宿主机** | `deploy-msop.sh:107-109` | 远程/容器化执行时数据不是集群真实值 |
| 13 | `verify-web-arm64.sh` 用 `docker.1ms.run/curlimages/curl` | 脚本第 4 行 | 依赖外部镜像源，气隙不可用 |
| 14 | `down` 保留命名空间与前端 | `deploy-msop.sh:112-119` | 有意为之，但需知道「down 不等于清空」 |

这些不是要「改造厂商」，而是**我们自己的包不能继承**——每一条在本包里都有对应处置（§3）。

### 2.2 `fix-node-clock.sh` 为什么没复制

它修的是**节点工程问题**（VMware 虚机 RTC 存本地时间、guest 按 UTC 解释，
导致开机 20 秒内签发的 SA token 时间在未来，之后全集群 401）。
根因在 vSphere 的 `rtc.diffFromUTC`，脚本只是缓解三层。处置：

- 不放进部署包（它是节点级、需 root、且改 systemd/kubelet 配置，不属于应用交付物）；
- 但在 `README-OFFLINE.md` 与本节记录现象与判据：`kubectl get node` 正常、
  但 CoreDNS/kube-proxy/Calico 集体 `Unauthorized` ⇒ 先查时钟，别急着重建集群；
- **气隙前提**：`systemctl enable systemd-time-wait-sync` 需要内网 NTP，
  否则 kubelet 会一直等到同步成功而永不启动。上线前必须确认授时源。

## 3. 本包的对应设计

| 厂商 | 本包 | 设计取舍 |
| --- | --- | --- |
| `deploy-msop.sh up` | `deploy.sh install` | 子命令拆细：`preflight / install / schema / seed / status / verify / switch / down` |
| 口令明文 | Secret `paas-db`（随机生成，不落盘到清单） | 口令只存在于集群 Secret 与运维手里 |
| `docker run` 灌库 | `15-schema-job.yaml`（集群内 Job） | 不依赖宿主工具，按 DNS 解析，镜像随包分发 |
| 硬编码 IP/NS/class | 占位符 `__NS__ __MYSQL_HOST__ __INGRESS_CLASS__ ...`，由 `deploy.sh` 渲染 | 渲染后**校验无残留占位符**，漏一个就退出 |
| 7 个服务 | `20-backend.yaml`（1 个） | 只替换 `gaop-be` |
| 无镜像校验 | `images/*.tar.gz` + `SHA256SUMS` + `MANIFEST.txt` | 解包可验、可追溯 git 与 SQL 指纹 |
| 无切流方案 | `deploy.sh switch paas\|vendor [web\|api\|all]` | 按 path 定位再改 backend，不假设索引为 0 |
| 无回退闸 | `down` 检测入口仍指 `paas-*` 时**拒绝执行** | 防止「删了服务页面白屏」 |
| `verify-web-arm64.sh` 拉外部镜像 | `deploy.sh verify` 在 `paas-core` 容器里 `curl` | 后端镜像自带 curl，零额外依赖 |
| `fix-node-clock.sh` | 不复制，文档记录现象与判据 | 节点级不属于应用交付物 |

### 3.1 与厂商的行为差异（按项目 A/B/C 口径）

- **B（主动收紧，成功路径响应一致）**：
  - 后端**非 root** 运行（uid 10001），厂商镜像以 root 跑；
  - 默认 RBAC 收窄到 `paas-core-manager`（`RBAC=admin` 可回退到厂商等价的 cluster-admin）；
  - 口令随机化 + Secret，不在清单里出现；
  - 灌库走 Job，不在宿主机执行 docker。
- **C（环境约束，按代码实现并标注）**：
  - 清单**不声明 CPU**：本集群 2 核 requests 已满，且「只写 limit」会被 K8s 默认成 request。
    通用环境请补 `requests.cpu=200m / limits.cpu=2000m`（`20-backend.yaml` 有注释）；
  - `paas-web` 仍以镜像默认用户（root）跑：nginx 官方镜像 entrypoint 需要绑定 80。
    彻底非 root 要换 `nginx-unprivileged` + 端口 8080，属后续项。
- **A（厂商缺陷，不复制）**：
  - 不复制 `gaop123456`、`10.96.1.127`、`hostPath ... type: Socket`、`set -uo`（缺 `-e`）。

## 4. 产物与构建

```bash
deploy/build-image.sh        # 后端镜像：mvn package → docker build → docker save
deploy/build-package.sh      # 离线包：三个镜像 + 清单 + SQL → tar.gz → 解包自检
```

`build-package.sh` 的自检会在临时目录解包，做四件事：`bash -n` 脚本语法、
用示例值渲染全部清单（校验无占位符残留）、逐个 `tar tzf` 验证镜像包未损坏、
按 `SHA256SUMS` 校验。

镜像基础层：

| 镜像 | 基础 | 说明 |
| --- | --- | --- |
| `paas-core:<ver>-arm64` | `eclipse-temurin:21-jre-alpine` | JRE21，非 root，堆随 memory limit（`MaxRAMPercentage=60`） |
| `paas-web:<ver>-arm64` | `nginx:1.22.0` | 与厂商同版本；产物架构无关，见 `paas-web/deploy/Dockerfile` |
| `mysql:8.0.32-arm64` | 官方镜像 | 只用于灌 schema 的 Job |

## 5. 已知未覆盖

- **FastDFS 上传链路**：`/api/gaop/v1/pub/fdfs/upload` 仍指厂商 `gaop-fdfs`；本包不接管，
  也没有在无 FastDFS 的环境验证过上传。
- **多副本**：`paas-core` 用 `maxSurge: 0 / maxUnavailable: 1`，且依赖同一个 Redis 会话。
  水平扩容前要先确认会话粘性或共享会话（Redis 已支持），未做验证。
- **`--with-redis` 起的 `paas-redis` 无持久化**：与厂商一致（纯缓存语义），重启即清空。
- **HTTPS/TLS**：入口证书由现场网关负责，本包不涉及。
