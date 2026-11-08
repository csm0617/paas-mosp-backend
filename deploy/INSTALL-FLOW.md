# 安装流程

从拿到 `paas-deploy-<ver>-arm64.tar.gz` 到流量切到自研前后端的完整路径。
每一步都对应 `deploy.sh` 里真实存在的命令；文档里出现的命令都能直接复制执行。

> 两条路径共用同一套介质与步骤，只在「数据库」一节分叉：
> **首次安装**（空库）走全流程；**替换已有环境**跳过 schema/seed。

## 0. 总览

| 阶段 | 做什么 | 命令 | 改什么 | 失败的样子 |
| --- | --- | --- | --- | --- |
| A 进场前 | 节点与依赖体检 | `precheck-node.sh` | 不改 | 明确报出哪项不满足 |
| B 介质准备 | 校验、解包、导入镜像 | `shasum -c` / `docker load` | 节点镜像库 | 校验失败 / ImagePullBackOff |
| C 参数确认 | 定下命名空间与连接串 | 环境变量 / `package.env` | 不改 | 后面每一步都连错库 |
| D 数据库 | 空库建表 + 播种 | `install --with-schema --with-seed` | 建库、39 张表 | Job 失败即停（backoffLimit=0） |
| E 安装 | 体检 + apply 应用层 | `doctor` / `install` | 新增 paas-* 对象 | rollout 超时，脚本打日志后退出 |
| F 验收 | 冒烟 4~5 项 | `verify` | 不改 | 返回非 0，逐项标红 |
| G 切流 | 改 Ingress backend | `switch paas` | 改厂商 ingress 指向 | 页面 404/白屏 |
| H 回退/卸载 | 切回或删除 | `switch vendor` / `down` | 反向操作 | `down` 被安全闸拒绝 |

**为什么是这个顺序**（不是可以随便打乱的清单）：

1. **镜像必须先于 install**——清单是 `imagePullPolicy: IfNotPresent`，节点没有镜像就是
   `ImagePullBackOff`，而 `install` 只等 rollout，不会替你拉镜像。
2. **schema 必须先于后端启动**——后端启动早期会查 `g_user` 等表，表不存在直接
   `BadSqlGrammarException` → Bean 创建失败 → 容器起不来（厂商 `FLYWAY_ENABLE=false`
   也是这个前提：schema 由安装器预先灌入）。
3. **seed 必须在 schema 之后**——它往 `g_role`/`g_user`/`g_user_role` 插数据。
4. **verify 必须在 switch 之前**——先确认新 Pod 自己能服务，再把流量交出去；
   否则回退时你分不清是"新服务坏了"还是"切流切错了"。
5. **down 之前必须 switch vendor**——脚本有安全闸，入口还指着 `paas-web` 时拒绝删除。

## A. 进场前（不碰集群）

```bash
# 每台要跑容器的机器上执行，只读
sudo ./precheck-node.sh
```

它查 11 项，其中三项是**硬门槛**（不满足直接失败）：

| 硬门槛 | 判据 | 不满足的后果 |
| --- | --- | --- |
| swap 关闭 | `/proc/swaps` 无 `/` 开头行 | kubelet 拒绝启动 |
| 默认路由恰好 1 条 | `ip route \| grep -c '^default'` = 1 | Pod 出网方向不确定，间歇性连不上 API |
| NTP 已同步 | `timedatectl show -p NTPSynchronized` = yes | 时钟回拨 → SA token 的 `iat` 在未来 → **全集群 401**（本项目真实踩过） |

其余是提示项（磁盘 < 20G、内存 < 4G、`ip_forward` 未开、`br_netfilter` 未加载、
SELinux Enforcing、firewalld 运行中、root 未设密码、外网域名解析失败）。

> 非 root 执行会跳过 root 密码检查；气隙环境"外网域名解析失败"是正常的。

## B. 介质准备

```bash
tar xzf paas-deploy-<ver>-arm64.tar.gz
cd paas-deploy-<ver>-arm64

# ① 整包与包内镜像完整性
shasum -a 256 -c ../paas-deploy-<ver>-arm64.tar.gz.sha256   # 整包
shasum -a 256 -c SHA256SUMS                                 # 包内三个镜像

# ② 导入镜像
#   docker：
for f in images/*.tar.gz; do docker load -i "$f"; done
#   containerd：
for f in images/*.tar.gz; do ctr -n k8s.io images import "$f"; done

# ③ 确认 tag 与 package.env 一致
docker images | grep -E 'paas-core|paas-web|mysql'
cat package.env
```

期望看到三个 tag：`paas-core:<ver>-arm64`、`paas-web:<webver>-arm64`、`mysql:8.0.32`。
`package.env` 是打包时固化的默认值，`deploy.sh` 会 source 它；现场若显式设了
`BACKEND_IMAGE`/`FRONTEND_IMAGE`，以现场为准。

`bin/kubectl` 不用手动处理：`deploy.sh` 在 Linux 上会自动前置到 `PATH`。

## C. 参数确认

最小集（其余走默认）：

```bash
export NS=gaop-namespace
export MYSQL_HOST=gaop-mysql          # 现场 MySQL 的 Service 名
export REDIS_HOST=gaop-redis
```

按需覆盖：

| 变量 | 什么时候必须改 |
| --- | --- |
| `DB_NAME` / `APP_USER` | 现场库名/账号不是 `gaop` |
| `MYSQL_PASSWORD` | 现场已有应用账号口令（不传则随机生成并写 Secret） |
| `MYSQL_ROOT_PASSWORD` | **只有要建库建账号时才需要**；不传则跳过建库 |
| `INGRESS_CLASS` | 现场不是 `higress` |
| `GW` | `verify` 要测网关时填，如 `172.16.209.170` |
| `RBAC` | 受限 ClusterRole 不够用时改 `admin`（等价厂商的 cluster-admin） |

建议把这些写进一个 `env.sh` 里 `source` 后再执行，避免每条命令重复传。

## D. 数据库（只在空库时需要）

先判断现状，不要盲目灌：

```bash
./deploy.sh doctor        # 会报命名空间/依赖 Service/镜像/重复安装
# 直接确认表数（替换式环境下库通常已由厂商安装器初始化）
kubectl -n "$NS" run dbq --rm -i --restart=Never \
  --image=mysql:8.0.32 --command -- mysql -h "$MYSQL_HOST" -u"$APP_USER" -p"$MYSQL_PASSWORD" -N -B \
  -e "select count(*) from information_schema.tables where table_schema='$DB_NAME';"
```

| 结果 | 走法 |
| --- | --- |
| 表数 ≈ 39 | **跳过本阶段**，直接 E（这是替换式环境的常态） |
| 库不存在 / 表数为 0 | 执行下面的初始化 |

```bash
MYSQL_ROOT_PASSWORD='<root口令>' MYSQL_PASSWORD='<应用账号口令>' \
  ./deploy.sh install --with-schema --with-seed --skip-wait
```

这一条会依次做：建库（utf8mb4）→ 建 `mysql_native_password` 账号 → 按 `sort -V`
灌 87 个迁移 → 播种 `admin`。`--skip-wait` 是因为此时后端还没 apply，等它没意义
（若不想分步，去掉 `--skip-wait` 让 install 一次做完也行）。

预期输出：`成功=87 失败=0`、`表数=39`、seed 的三行 `role/user/mapping`。

失败处置：

- 报 `Table ... already exists` → 库非空，**不要重试**，改用已有库路径；
- 报 `Access denied` → `MYSQL_ROOT_PASSWORD` 不对，或账号没有 DDL 权限；
- 报连接超时 → `MYSQL_HOST` 不是本命名空间的 Service，且不是可达的外部地址。

## E. 安装

```bash
./deploy.sh doctor        # 7 项集群侧体检，不改任何东西
./deploy.sh install
```

`install` 的实际顺序（与脚本一致）：

1. 渲染 7 份清单（占位符替换，残留即退出）
2. `preflight` 最低门槛检查
3. `10-foundation.yaml`：ServiceAccount `paas-core` + ClusterRole（默认受限）+ Binding
4. Secret `paas-db`（不存在才建；口令随机生成或取 `MYSQL_PASSWORD`）
5. 可选 `15-schema-job` / `16-seed-job` / `11-redis-optional`
6. `20-backend.yaml`（`paas-core` Deployment + Service）
7. `30-frontend.yaml`（`paas-web` Deployment + Service）
8. 等 `paas-core` rollout（上限 600s）、`paas-web`（上限 180s）
9. 可选 `40-ingress.yaml`（**只有空环境才用**）
10. 打印 `status`

观察点：

- `paas-core` 有 `startupProbe`（5s × 60 = 5 分钟窗口）。首次启动会做 MyBatis 初始化与
  fabric8 客户端 discovery，**前 30~60 秒 readiness 可能是 `OUT_OF_SERVICE`**，属正常；
- 真正的失败信号是 rollout 超时——脚本会自动打 `describe pod` 与最近 60 行日志。

> ⚠️ 不要在这个阶段执行 `switch`。此时流量还在厂商侧。

## F. 验收

```bash
GW=172.16.209.170 ./deploy.sh verify
```

五项检查（全部在后端容器里用 curl 执行，不依赖目标机工具）：

| # | 检查 | 通过判据 |
| --- | --- | --- |
| 1 | 后端健康 | `/actuator/health` 含 `"status":"UP"` |
| 2 | 前端静态 | `http://paas-web/index.html` = 200 |
| 3 | 后端公开端点 | `/v1/role?username=admin` = 200（登录页下拉依赖，未登录可访问） |
| 4 | 网关前端 | `http://$GW/gaop/` = 200 |
| 5 | 网关 API | `http://$GW/api/gaop/v1/role` = 200 |

第 4、5 项只在设了 `GW` 时执行。**注意**：若此时还没 `switch`，第 4、5 项走的仍是
厂商服务——它们验证的是"网关通"，不是"自研接管"。真正验证自研接管要在 G 之后重跑。

人工验收（可选但建议）：

```bash
# 集群内直接打后端：登录拿 token，再带 Cookie 打受保护端点
kubectl -n gaop-namespace exec deploy/paas-core -- \
  curl -s -X POST http://127.0.0.1:8080/v1/user/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","role":"admin"}'
```

期望 `{"msg":"OK","code":0,...,"token":"<36位>"}`。拿到 token 后带
`Cookie: gateway-token=<token>` 访问 `/v1/user/list`、`/v1/role/list`，都应 200。

## G. 切流

**建议顺序：先后端、后前端**（前端先切意味着自研前端直连厂商后端，契约虽对齐但
未经真实 e2e；先后端则两边都能回退）。

```bash
./deploy.sh switch paas api      # 只切 API：gaop-api-be → paas-core
./deploy.sh verify               # 重跑，这次第 4/5 项验的是自研
./deploy.sh switch paas web      # 再切前端：gaop-web → paas-web
```

每一步都是改 Ingress 的 backend service 名（JSON patch 定位 path，不假设索引为 0），
实测生效时间约 5 秒。

回退：

```bash
./deploy.sh switch vendor        # 一次性把两个 ingress 指回厂商
```

回退不重新部署、不动数据库——两边共用同一个 `gaop` 库（ADR-0005）。

## H. 卸载

```bash
./deploy.sh switch vendor        # 必须：脚本会检查，入口还指着 paas-* 就拒绝 down
./deploy.sh down
```

`down` 删除：`paas-core`/`paas-web` 的 Deployment 与 Service、两个 Job、两个 ConfigMap、
`40-ingress` 的三条 ingress。
**不删**：命名空间、数据库数据、Secret `paas-db`、厂商任何对象。

## 附：与厂商安装流程的层次对照

厂商 `msop-v1.3.4` 的 4 个 playbook 是**装平台**（加节点），本包是**换应用**。
两者的阶段不可直接对齐，容易误判"我们的包少了东西"：

| 厂商步骤 | 干的事 | 本包对应 |
| --- | --- | --- |
| `add-node-prepare.yml`（1/4） | precheck-system / ssh / install-paas-prepare / site-ntp | `precheck-node.sh`（只查不改） |
| `add-node-docker.yml`（2/4） | CNI / docker / cri-docker / crictl / mydumper | **不做**：现场已有运行时 |
| `add-node-kubernetes.yml`（3/4） | etcd / vip / kubeadm join / flannel / minio / diagnostic / gpu / k9s | **不做**：现场已有集群与中间件 |
| `add-node-minio-replicate.yml`（4/4） | MinIO 多节点复制 | **不做**：对象存储不在替换范围 |

本包假定 A 阶段的三件事已经成立（集群、运行时、中间件），只负责应用层与数据层。
这也是为什么它只有 381MB——厂商包 2.8G 里绝大部分是 amd64 的节点组件与基础镜像。
