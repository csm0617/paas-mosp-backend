# paas-deploy 离线包：目标机操作手册

本包由 `paas-core/deploy/build-package.sh` 生成，**自包含**：三个 arm64 镜像、
K8s 清单、数据库脚本、编排脚本都在里面。目标机不需要联网、不需要 registry、
不需要 Maven / Node。

> 需要**逐步操作顺序、每步的判定与失败处置**时看 `INSTALL-FLOW.md`（同在本包里）。
> 本文是"包里有什么、每条命令怎么用"的速查。

## 1. 包里有什么

| 路径 | 内容 |
| --- | --- |
| `deploy.sh` | 编排入口：`doctor` / `up` / `preflight` / `install` / `switch` / `status` / `verify` / `down` |
| `INSTALL-FLOW.md` | **安装流程**：阶段划分、顺序约束、每步判定与失败处置 |
| `precheck-node.sh` | **节点本机**前置检查（swap/路由/DNS/时钟/磁盘/转发），只读 |
| `bin/kubectl` | 自带 linux/arm64 kubectl v1.31.9 —— 目标机**不需要预装 kubectl** |
| `k8s/` | 参数化清单：`10-foundation`(SA+RBAC) `11-redis-optional` `15-schema-job` `16-seed-job` `20-backend` `30-frontend` `40-ingress` |
| `sql/migration/` | 87 个迁移（V0.0.1 → V0.8.6），灌完 39 张表 |
| `sql/seed.sql` | 最小可登录数据（admin / 运维管理员门户） |
| `images/` | `paas-core-arm64-<ver>.tar.gz`、`paas-web-arm64-<ver>.tar.gz`、`mysql-8.0.32-arm64.tar.gz` |
| `MANIFEST.txt` | 版本、git、镜像 sha256、kubectl 版本、SQL 目录指纹 |
| `SHA256SUMS` | 镜像包校验和 |

清单里的占位符（`__NS__`、`__BACKEND_IMAGE__` 等）由 `deploy.sh` 在运行时渲染，
**不要手工 sed 清单**——漏一个就会 apply 出字面量对象。

`package.env` 记录本包的镜像 tag，是「包里到底装了什么」的唯一权威。前端迭代期它可能
与 `paas-web/package.json` 差一个版本（打包快照早于最后一次提交），**以包里为准**；
`MANIFEST.txt` 里的 `frontend:` 行记录的就是实际打进去的版本。

## 2. 前置条件

- 目标节点 **linux/arm64**，集群 ≥ v1.24（清单用 `networking.k8s.io/v1`）。
- 现场已有 **MySQL 8** 与 **Redis**（替换式部署复用它们）。地址通过环境变量传入，见 §5。
- 容器运行时是 docker 或 containerd，二者导入命令不同（见 §3）。
- **不需要预装 kubectl**：包里 `bin/kubectl` 是 linux/arm64 的 v1.31.9，`deploy.sh`
  会把它前置到 `PATH`（优先于目标机的任何版本）。

进机房前，先在**每台要跑容器的机器上**跑一遍节点检查（只读，不改东西）：

```bash
sudo ./precheck-node.sh
```

它查的是 K8s API 里看不到的东西，也是厂商「安装注意事项」的四条 + 我们实际踩过的坑：

| 检查 | 为什么 | 不满足的后果 |
| --- | --- | --- |
| swap 必须关闭 | kubelet 硬要求 | kubelet 直接拒绝启动 |
| 默认路由恰好 1 条 | 多网关会让 Pod 出网方向不确定 | 间歇性连不上 API/仓库 |
| DNS 有 nameserver | 镜像与集群 DNS 都依赖 | 拉不到镜像、域名解析超时 |
| root 可登录（厂商要求） | Ansible/调试入口 | 后续运维动作受阻 |
| **NTP 已同步** | SA token 有 iat/exp 硬校验 | 时钟回拨 → 全集群 401（本项目真实踩过） |
| 磁盘 ≥ 20G / 内存 ≥ 4G | 镜像 400MB + 容器层与日志 | 导入镜像失败、节点 OOM |
| `ip_forward=1`、`br_netfilter` | K8s 网络前提 | Service/ClusterIP 不通 |

> 非 root 执行会跳过 root 密码一项；其余项不依赖 root。

> 本包**不**部署 MySQL / MinIO / FastDFS / 网关。这些是厂商侧的既有设施，
> 替换式部署的原则是「只换应用层」，把它们一起接管会让回退面变大。

## 3. 导入镜像

```bash
tar xzf paas-deploy-<ver>-arm64.tar.gz
cd paas-deploy-<ver>-arm64
shasum -a 256 -c SHA256SUMS          # 先验包内镜像完整性

# 运行时是 docker：
for f in images/*.tar.gz; do docker load -i "$f"; done
docker images | grep -E 'paas-core|paas-web|mysql'

# 运行时是 containerd：
for f in images/*.tar.gz; do ctr -n k8s.io images import "$f"; done
```

> 镜像 tag 带 `-arm64` 后缀（如 `paas-core:1.0.0-arm64`），与清单默认值一致。
> 混架构集群里用后缀区分，别在 amd64 节点调度。

## 4. 部署（替换式）

一条命令走完「体检 → 安装 → 冒烟」：

```bash
NS=gaop-namespace MYSQL_HOST=gaop-mysql REDIS_HOST=gaop-redis ./deploy.sh up
```

`up` 依次做三件事，任何一步失败就停：

1. **doctor** —— 7 项集群侧体检（kubectl 来源、服务端版本 ≥1.24、节点架构/Ready/压力、
   IngressClass、命名空间与依赖 Service、镜像是否已 load、重复安装检测）；
2. **install** —— SA + RBAC → Secret → 应用层 → 等 rollout；
3. **verify** —— 健康、前端、公开端点（见 §7）。

想分步做（首次上线建议）：

```bash
./deploy.sh doctor                       # 先看清环境，不改任何东西
./deploy.sh install                      # 不灌库：假定现场库已初始化
./deploy.sh verify
./deploy.sh switch paas                  # 切流（改 Ingress backend，秒级）
```

部署完成后 `paas-core` 与 `paas-web` 与厂商的 `gaop-be` / `gaop-web` **同时存在**，
流量仍走厂商——直到 `switch paas`。

### 回退

```bash
./deploy.sh switch vendor     # Ingress 指回 gaop-web / gaop-be
./deploy.sh status            # 确认指向
```

回退**不重新部署、不回滚数据库**：两边共用同一个 `gaop` 库，这是 ADR-0005 的前提。

## 5. 参数（按现场覆盖）

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `NS` | `gaop-namespace` | 目标命名空间 |
| `MYSQL_HOST` | `gaop-mysql` | MySQL Service 名；外部库填 ExternalName 或域名 |
| `MYSQL_PORT` | `3306` | |
| `DB_NAME` / `APP_USER` | `gaop` / `gaop` | 库名与账号 |
| `MYSQL_PASSWORD` | 自动生成 | 不传则随机生成并写进 Secret `paas-db`；**要在 MySQL 侧同步这个口令** |
| `MYSQL_ROOT_PASSWORD` | 空 | 只有给了才会执行「建库+建用户」 |
| `REDIS_HOST` / `REDIS_PORT` | `gaop-redis` / `6379` | |
| `GATEWAY_HOST` / `GATEWAY_PORT` | `gaop-gateway-auth` / `5000` | 规则通知目标 |
| `GIS_URL` | `gis-server:8080` | 被当作 Deployment 名读状态 |
| `INGRESS_CLASS` | `higress` | 只有 `--with-ingress` 才用到 |
| `RBAC` | `scoped` | `scoped`=受限 ClusterRole；`admin`=cluster-admin（与厂商等价） |
| `GW` | 空 | `verify` 时测的网关地址，如 `172.16.209.170` |

`install` 选项：`--with-schema` `--with-seed` `--with-redis` `--with-ingress` `--skip-wait`。

## 6. 空库首次初始化

```bash
MYSQL_ROOT_PASSWORD='<root口令>' \
MYSQL_PASSWORD='<应用账号口令>' \
./deploy.sh install --with-schema --with-seed
```

`--with-schema` 会跑 `15-schema-job`：建库（utf8mb4）→ 建 `mysql_native_password`
账号 → 按 `sort -V` 顺序灌 87 个迁移 → 打印表数。**失败即停**（`backoffLimit: 0`），
不会出现「灌一半再重试」把真实原因掩盖。

`--with-seed` 播种 `admin / admin123`。

> ⚠️ `admin123` 是测试口令。投产前替换 `g_user.password` 的 BCrypt 串，
> 生成方式见 `sql/seed.sql` 文件头。

**不要对已有库重复执行 `--with-schema`**：`CREATE TABLE` 会报已存在。
部署脚本默认不灌，只有显式 `--with-schema` 才动库。

## 7. 验证

```bash
GW=172.16.209.170 ./deploy.sh verify
```

检查五项：后端 `/actuator/health` = UP、`http://paas-web/index.html` = 200、
后端 `/v1/role?username=admin` = 200（登录页下拉依赖的**公开**端点，未登录即可访问）、
网关 `/gaop/` 与 `/api/gaop/v1/role`。

`verify` 是在 `paas-core` 容器里执行 `curl`（镜像自带），因此**不需要**
目标机有 curl，也不需要额外扣一个临时 Pod。

## 8. 排错

| 现象 | 原因 / 处置 |
| --- | --- |
| `kubectl: command not found` | 包内 `bin/kubectl` 缺失（打包时被跳过）。用系统 kubectl 也行，或重新解包 |
| `bin/kubectl: cannot execute binary file` | 在非 Linux/非 arm64 上跑了包内二进制。包里只有 linux/arm64，换到目标机执行 |
| Pod `exec format error` | 节点不是 arm64；`kubectl get node -o wide` 看 ARCH |
| `ImagePullBackOff` | 镜像没导入，或 `imagePullPolicy` 被改成 Always。先 `docker load` |
| `paas-core` 启动后 401/403 访问 K8s API | RBAC 不够。日志会带 resource+verb，按需补 `10-foundation.yaml` 的 ClusterRole，或 `RBAC=admin` |
| Pod `Pending`、事件 `Insufficient cpu` | 节点 CPU requests 已满。清单刻意不声明 CPU；若有人加了 `cpu limit`，K8s 会把它默认成 request |
| readiness 一直红，日志里 MySQL 连不上 | `MYSQL_HOST` 不对，或口令与库里不一致（见 §5 的 `MYSQL_PASSWORD`） |
| readiness 前 30~60 秒是 `OUT_OF_SERVICE` | 正常启动过程：Spring Boot 的 readiness 要等 Web 容器与 K8s 客户端就绪后才切 `ACCEPTING_TRAFFIC`。`startupProbe` 给 5 分钟窗口，等它自己转 UP，不要据此重启 Pod |
| 登录页「席位不能为空」 | 没播种：`./deploy.sh seed`，或用户/角色关联缺失 |
| 切换后页面白屏 | Ingress 的 rewrite 把前缀处理错了；本包 nginx 两种拓扑都支持，先确认 `curl -I http://<gw>/gaop/` 的 200 落在谁身上 |
| `down` 被拒 | 入口还指着 `paas-web`，先 `./deploy.sh switch vendor` |

## 9. 卸载

```bash
./deploy.sh switch vendor     # 必须先切回
./deploy.sh down              # 删 paas-core / paas-web / Job / ConfigMap
```

`down` **不删**：命名空间、数据库数据、Secret `paas-db`、厂商任何对象。
Secret 里可能有厂商后端正在用的口令，清理前先确认。
