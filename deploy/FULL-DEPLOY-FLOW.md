# 完整部署流程：裸机 → MSOP 基线 → 自研替换

把两份文档接起来：`VENDOR-CLUSTER-FLOW.md`（厂商从裸机到 MSOP）与 `INSTALL-FLOW.md`
（我们从 MSOP 到自研）。本文是**端到端那条线**——每一段的输入、输出、交接判据。

## 0. 总览：五段、两个包、一条责任线

| 阶段 | 谁做 | 用什么 | 产出 | 交接判据（Gate） |
| --- | --- | --- | --- | --- |
| **S1 裸机** | 客户 / 实施 | 无（人工 + `precheck-node.sh`） | 3 台以上机器、网络、root | 节点体检无失败项 |
| **S2 集群与中间件** | 厂商包 | `msop.tar.gz` → `install-all.sh` | K8s 1.27.3 + 外部 etcd + MinIO + registry + OpenEBS + MySQL/Redis + 监控 | 33 步全绿；`kubectl get node` 全 Ready |
| **S3 MSOP 基线** | 厂商包（同上，业务段） | `roles/gaop` + `install-paas-init-sql.yml` | `gaop-*` 九个对象 Running、87 个 SQL 灌完 | 浏览器能打开登录页并用 admin 登录 |
| **S4 自研替换** | **我们** | `paas-deploy-<ver>-arm64.tar.gz` | `paas-core` + `paas-web` 并存，切流后接管 | `verify` 五项 + 业务回归 |
| **S5 运维** | 双方 | 各自的包 | 升级 / 加节点 / 回退 | — |

两个包**不是替代关系**：厂商包负责「把机器变成一套能跑 MSOP 的 K8s」，我们负责
「把 gaop-be / gaop-web 换成自研实现」。所以 S2、S3 是 S4 的前提，不能跳过。

## 1. S1 裸机准备

**机器**：至少一台控制面（也可以 all-in-one）。厂商默认 `cluster_allinone=false`，
`remote_dir=/data/paas`。

**系统**：麒麟 V10（厂商 `deps/packages-deb-kylin/` 就是给它的）或主流 deb 系；
`host/hosts` 的示例主机名是 `kylin-master`，说明主推麒麟。

**必查项**（在每台机器上跑，只读）：

```bash
sudo ./precheck-node.sh
```

三条硬门槛：**swap 关闭**、**默认路由恰好 1 条**、**NTP 已同步**。
第三条尤其重要——时钟回拨会让 SA token 的 `iat` 落在未来，全集群 401（本项目真实踩过）。

**网络**：单一默认网关；内网 NTP 源（气隙环境必须自备）；多 master 需要一个可漂移的 VIP，
由厂商的 **`vip-daemon`**（自研 Go 服务 + systemd）维护，**不是 keepalived**。
镜像仓库是 `registry:2.8.3`（NodePort **1080**），**不是 Harbor**——业务变量里的
`HARBOR_HOST` 只是历史命名。细节见 `VENDOR-CLUSTER-FLOW.md` §4.1。

**目录**：`/data/msop`（安装包）、`/data/paas`（数据与二进制落盘）。

## 2. S2 集群与中间件（厂商 `install-all.sh`）

```bash
# ① 介质就位
mv /data/msop /data/msop-bak 2>/dev/null
tar xf msop.tar.gz -C /data           # 得到 /data/msop
cd /data/msop

# ② 改 inventory（必须改，示例里是明文口令 + 单 master）
vi host/hosts
```

`host/hosts` 要改三处：

```ini
[k8s-master]
192.168.0.101 hostname=kylin-master label_nvidia=false
[k8s-worker]
192.168.0.102 hostname=kylin-worker01 label_nvidia=false    # 放开
[gaop-vip-addresses]
192.168.0.100 interface=bond6                                # 多 master 才需要
[all:vars]
ansible_password="<改成现场 root 口令>"                       # 示例是明文 glch.722
default_interface=bond6                                      # 改成实际网卡
```

```bash
# ③ 改集群参数（单 master 也要改 IP）
vi k8s-yaml/kubeadm_init.yaml
#   localAPIEndpoint.advertiseAddress
#   controlPlaneEndpoint: "<VIP 或 master IP>:6443"
#   etcd.external.endpoints: ["https://<etcd IP>:2379"]

# ④ 执行（会先装 podman，再在容器里跑 ansible）
./install-all.sh
```

`install-all.sh` 失败会 `exit 1`。33 步的分段见 `VENDOR-CLUSTER-FLOW.md` §3，
其中与后续阶段强相关的四段：

| 段 | 步骤 | 为什么后面依赖它 |
| --- | --- | --- |
| 运行时 | docker 24.02 → cri-dockerd → crictl | 后面所有 Pod 都跑在这个运行时上 |
| 控制面 | 外部 etcd → VIP → kubeadm init → join | S4 的 `kubectl` 操作前提 |
| 中间件 | MinIO → registry → OpenEBS | 业务镜像仓库与对象存储 |
| 业务基础 | 推镜像 → static-token → MySQL 数据迁移 → `roles/gaop` | **S3 的产物直接由它产生** |

**Gate 1**：

```bash
kubectl get node                       # 全 Ready
kubectl -n gaop-namespace get po       # 无 CrashLoopBackOff
kubectl get ingressclass               # 记下类名（S4 要用 INGRESS_CLASS）
```

## 3. S3 MSOP 基线

这一步由 S2 的 `install-paas-basic-server.yml` + `install-paas-init-sql.yml` 完成，
产出就是「一套能登录的 MSOP」。验收：

```bash
kubectl -n gaop-namespace get deploy,sts,svc
# 期望看到：gaop-be / gaop-web / gaop-auth / gaop-gateway-auth / gaop-fdfs /
#          gaop-image-uploader / gaop-startup / gaop-mysql / gaop-redis
```

### ⚠️ 这里有两套 MySQL，别认错

| 对象 | 类型 | 用途 | 谁在用 |
| --- | --- | --- | --- |
| `gaop-radondb-mysql-leader` | RadonDB MySQL **StatefulSet** 集群的 leader service | **业务主库**（`RADONDB_NAME=gaop`） | `gaop-be` 的 `MYSQL_HOST` |
| `gaop-mysql` | 普通 **Deployment**（`mysql:8.4.4`） | 只带一个 init 脚本，给框架/工具用 | `gaop-be` 的 `GAOP_MYSQL_HOST` |

`k8s-yaml/gaop-be.yaml` 里的实际配置是：

```
MYSQL_HOST=gaop-radondb-mysql-leader   MYSQL_USERNAME=root   MYSQL_PASSWORD=123456
GAOP_MYSQL_HOST=gaop-mysql
REDIS_HOST=gaop-redis
```

**所以 S4 连库必须指向 `gaop-radondb-mysql-leader` + `root`**，指向 `gaop-mysql`
会读到一个空的/无关的库——这正是我们部署包之前的默认值错误，已修正。

### 业务库初始化顺序

`install-paas-init-sql.yml` 里有一份**显式 87 项顺序表**（`sql_items`），
我们把它逐字复制成了 `sql/order.txt`。不要用 `sort -V` 代替：`V0.8.3` 的两个文件
厂商顺序与 `sort -V` 相反。

**Gate 2**：浏览器打开 `http://<VIP>/` → 登录页 → 用出厂账号登录 → 能看到门户下拉。
这一步过不了就别进 S4：S4 的验收会分不清是「自研坏了」还是「基线本来就坏」。

## 4. S4 自研替换（我们的包）

前提：Gate 1、Gate 2 都过。详细步骤见 `INSTALL-FLOW.md`，这里只列与 S2/S3 的衔接：

```bash
tar xzf paas-deploy-<ver>-arm64.tar.gz && cd paas-deploy-<ver>-arm64
for f in images/*.tar.gz; do docker load -i "$f"; done   # 或 ctr -n k8s.io images import

export NS=gaop-namespace
export MYSQL_HOST=gaop-radondb-mysql-leader   # ★ 业务库，不是 gaop-mysql
export APP_USER=root                          # ★ 与 gaop-be 一致
export MYSQL_PASSWORD='<现场 root 口令>'       # ★ root 必须显式给，不会随机生成
export REDIS_HOST=gaop-redis
export INGRESS_CLASS=<Gate 1 记下的类名>

./deploy.sh doctor
./deploy.sh install
./deploy.sh verify
./deploy.sh switch paas api     # 先后端
./deploy.sh verify              # 这次验的是自研
./deploy.sh switch paas web     # 再前端
```

**Gate 3**：`verify` 五项全绿 + 浏览器登录 → 控制台 → 刷新保持会话。

回退：`./deploy.sh switch vendor`（秒级，不动数据）。

### 与 S2/S3 共享的东西（不要重复创建）

| 资源 | 归属 | S4 的态度 |
| --- | --- | --- |
| `gaop-namespace` | S2 | 复用 |
| `gaop-radondb-mysql-leader` / `gaop-redis` | S2 | **只连不建**（`--with-redis` 才会另起 `paas-redis`） |
| `gaop-*` 九个业务对象 | S3 | 不动；`paas-core`/`paas-web` 并存 |
| Ingress `gaop-web` / `gaop-api-be` | S3 | **只改 backend 名**，不新建同 path 的 ingress |
| 87 个 SQL | S3 已灌 | S4 默认不灌（`--with-schema` 才灌，且会失败于「表已存在」） |

## 5. Gate 汇总（交付前逐项打勾）

- [ ] S1：`precheck-node.sh` 无失败项（swap / 路由 / NTP 三项硬门槛）
- [ ] S2：`kubectl get node` 全 Ready；外部 etcd 健康
- [ ] S2：`kubectl -n gaop-namespace get po` 无 CrashLoopBackOff
- [ ] S2：`INGRESS_CLASS` 已记录（S4 要用）
- [ ] S3：登录页可打开、admin 可登录、门户下拉有值
- [ ] S3：业务库表数符合预期（`RADONDB_NAME=gaop`）
- [ ] S4：`doctor` 无失败项
- [ ] S4：`install` 后 `paas-core` / `paas-web` Ready
- [ ] S4：`verify` 五项通过
- [ ] S4：切流后浏览器实测（登录、刷新、主要页面）
- [ ] S4：`switch vendor` 回退演练做过一次

## 6. 全链路最容易断的五个点

| # | 断点 | 症状 | 处置 |
| --- | --- | --- | --- |
| 1 | S1 的 NTP 没配 | 装到 S2 中途全集群 401，CoreDNS/CNI 全挂 | 先对时；删掉持旧 token 的 Pod |
| 2 | S2 的 `host/hosts` 没改网卡名 | `default_interface=bond6` 与实际不符，flannel/VIP 起不来 | 改 `default_interface` |
| 3 | S3 的库认错 | S4 连上 `gaop-mysql`，登录后看不到数据 | 确认 `MYSQL_HOST=gaop-radondb-mysql-leader` |
| 4 | S4 重复灌 SQL | schema Job 报 `Table ... already exists` | 已有库不要用 `--with-schema` |
| 5 | S4 抢 ingress path | 同一 host+path 两个 ingress，流量随机 | 用 `switch` 改 backend，不要 apply `40-ingress.yaml` |

## 7. 如果要全 arm64

| 段 | 现状 |
| --- | --- |
| S1 | 架构无关，`precheck-node.sh` 会检查 `aarch64` |
| S2 | **缺介质**：厂商 `msop.tar.gz` 只有 `amd64/`，但 `install-paas-prepare.yml` 有完整 ARM 分支。需要向厂商索取 `arm64/`（清单见 `VENDOR-CLUSTER-FLOW.md` §5） |
| S3 | 同上，业务镜像是 amd64（`192.168.0.101:1080/system_containers/*`），需要 arm64 版本 |
| S4 | **已全 arm64**：`paas-core`/`paas-web`/`kubectl` 都是 linux/arm64 |

也就是说：**S4 我们这一段已经 arm64 就绪，卡点在 S2/S3 的厂商介质**。
