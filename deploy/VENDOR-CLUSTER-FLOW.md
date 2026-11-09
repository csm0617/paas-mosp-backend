# 厂商安装流程（完整安装器）与 arm64 缺口

本文基于 **`msop.tar.gz`（4.3G，`VERSION=20260811` = v1.3.5-20260811）**。
它与之前那个 `msop-v1.3.4-20260416.tar.gz`（2.8G）不是同一个东西：

| 包 | 大小 | 定位 | 有主流程吗 |
| --- | --- | --- | --- |
| `msop-v1.3.4-20260416.tar.gz` | 2.8G | 节点侧介质 + 加节点入口（`add-node-*.yml`） | ❌ 21 个 include 全缺 |
| **`msop.tar.gz`** | **4.3G** | **完整安装器**（`install-all.sh` + `install-k8s-all.yml` + 37 个 role + `k8s-yaml/` + `host/`） | ✅ 33 个 include 全在 |

> 上一版本文的结论（「没有从 0 流程」）只对**第一个包**成立。
> 拿到完整包后已更正。

## 1. 入口：两条命令

```bash
# 在 /data/msop 下，以 root 执行
./install-all.sh
```

`install-all.sh` 全文 13 行，实质是：

```bash
source prepare-ansible.sh              # 装执行器
ansible-playbook install-k8s-all.yml   # 跑主流程
if [ $? -ne 0 ]; then echo "安装失败，中断脚本"; exit 1; fi
```

`install-all-arm-and-amd.sh` 与它**逐字相同**——没有额外的架构分支，架构判断在
`prepare-ansible.sh` 里做。

## 2. 执行器：`prepare-ansible.sh`（222 行）

这是整套流程能在裸机上跑起来的关键：**目标机不需要预装 ansible**。

1. 检测架构：`x86_64` → `amd64`，`aarch64` → `arm64`；
2. 装 podman + crun：读 `${arch}/ansible-env/podman-linux-${arch}.tar.gz`，把 `usr/` `etc/`
   rsync 到根，`crun` 放到 `/usr/local/bin/crun`（并删掉 `/usr/local/bin/runc`）；
3. 隔离 Podman 的 CNI 配置（`/etc/cni/net.d/87-podman-bridge.conflist` 移到
   `podman-cni-disabled/`）——否则 cri-dockerd 可能误用它；
4. `podman load` 导入 `ansible-env:${version}` 与 skopeo 镜像；
5. 起 `podman system service --time=0 unix:///tmp/podman.sock`；
6. 用 shell 函数覆盖 `ansible-playbook`：`podman run --rm -it --network host`，把当前目录、
   `/data/paas`、`/tmp`、`~/.ssh` 挂进去，在容器里执行真正的 ansible。

> 这一步决定了「离线可装」：ansible 本体、podman、skopeo 全部来自包内
> `${arch}/ansible-env/`，只有 podman 的二进制要落到宿主机。

## 3. 主流程：`install-k8s-all.yml`（33 个 include）

按文件里的顺序，阶段与职责如下（括号内是实际动作，读自对应剧本/role）：

| # | 步骤 | 干什么 |
| --- | --- | --- |
| 1 | `precheck-system.yml` | 系统预检 |
| 2 | `ssh.yml` | root 免密（`all:vars` 里有 `ansible_password`） |
| 3 | `install-paas-prepare.yml` | 主机名、工作目录、**按架构分发二进制**、systemd unit |
| 4 | `site-ntp.yml` | chrony/NTP 授时 |
| 5 | `install-paas-cni.yml` | cni-plugins（`cni-plugins-linux-*-v1.3.0.tgz`） |
| 6 | `install-paas-docker.yml` | docker 24.02（`docker_v24.02.tar.gz`） |
| 7 | `install-paas-cri-docker.yml` | cri-dockerd 0.3.4 |
| 8 | `install-paas-crictl.yml` | crictl v1.27.3 |
| 9 | `install-mydumper.yaml` | MySQL 逻辑备份工具 |
| 10 | `install-paas-etcd.yml` | **外部 etcd** + TLS（`/etc/etcd/pki/etcd_client.{crt,key}`） |
| 11 | `install-vip-daemon.yml` | **自研 `vip-daemon`**（Go 二进制 + systemd `vip.service`），见 §4.1 |
| 12 | `install-paas-kubernetes.yml` | 装 K8s 二进制 → 禁 swap → 主机名 → 系统参数 → `kubeadm init`（配置文件）→ 生成 static token → worker `kubeadm join` → 放开 NodePort 80-65535 → 设 `--node-ip` → 打 role 标签 |
| 13 | `install-paas-networks-flannel.yml` | flannel CNI（`kube-flannel.yml`） |
| 14 | `site-etcd-backup.yml` | etcd 定时备份（`roles/cron-etcd-bakdata`） |
| 15 | `install-paas-helm.yml` | helm 3 |
| 16 | `install-minio.yml` | MinIO（`roles/minio`） |
| 17 | `install-registry.yml` | 私有镜像仓库（`roles/registry`） |
| 18 | `install-paas-openebs.yml` | OpenEBS（`openebs-3.7.0.tgz`） |
| 19 | `install-paas-prometheus.yml` | Prometheus + node-exporter + cadvisor |
| 20 | `install-paas-loki.yml` | Loki + Promtail |
| 21 | `create-nodes-labels.yml` | 节点标签 |
| 22 | `install-paas-basic-server.yml` | 推业务镜像 → `static-token` role（master+worker）→ 给 apiserver 加 `--token-auth-file` → 迁移业务 MySQL 数据目录 → `roles/gaop` 部署业务 |
| 23 | `install-paas-init-sql.yml` | 灌业务库（**87 个迁移 + 初始化数据的来源**） |
| 24 | （内联） | `kubectl rollout restart deployment gaop-be -n gaop-namespace` |
| 25 | `install-paas-ingress-nginx.yml` | ingress-nginx（`4.8.3`）+ 业务 ingress |
| 26 | `install-other.yml` | 其它组件 |
| 27 | `install-machine-code.yml` | 机器码（授权） |
| 28 | `cron-del-completed-pod.yaml` | 定时清 Completed Pod |
| 29 | `install-diagnostic.yaml` | 诊断服务 + debug-agent DaemonSet |
| 30 | `install-gpu.yml` | GPU 插件（`label_nvidia=true` 时） |
| 31 | `install-kafka-ui.yml` | Kafka UI |
| 32 | `install-k9s.yaml` | k9s |
| 33 | `update-mysql-tls-cert.yml` | 更新 MySQL TLS 证书 |

**校验结果**：这 33 个 include 对应的文件 **33/33 全部存在**（第一个包里是 21/21 缺失）。

## 4. 关键参数（`k8s-yaml/kubeadm_init.yaml`）

| 项 | 值 |
| --- | --- |
| Kubernetes 版本 | **1.27.3** |
| 容器运行时 | cri-dockerd：`unix:///var/run/cri-dockerd.sock` |
| 控制面 | 单 master（`192.168.0.101:6443`），VIP 由自研 `vip-daemon` 维护（**不是** keepalived） |
| **etcd** | **外部**：`https://192.168.0.101:2379`，证书 `/etc/etcd/pki/etcd_client.*` |
| 认证 | `--token-auth-file=/etc/kubernetes/pki/static-token.csv`（静态 token，非 SA token） |
| 网络 | podSubnet `10.244.0.0/16`、serviceSubnet `10.96.0.0/12` |
| kube-proxy | **ipvs** |
| kubelet | `cgroupDriver: systemd`、`maxPods: 1000` |
| 镜像回收 | `imageGCHighThresholdPercent: 100` / `Low: 99`（**尽可能不删镜像**，与「离线 + IfNotPresent」策略一致） |
| 驱逐 | `memory.available: 100Mi`、`imagefs.available: 0%` |

inventory（`host/hosts`）：

```ini
[k8s-master]
192.168.0.101 hostname=kylin-master label_nvidia=false
[k8s-worker]
#192.168.0.102 hostname=kylin-worker01 label_nvidia=false
[gaop-vip-addresses]
#192.168.0.100 interface=bond6
[all:vars]
ansible_password="glch.722"
default_interface=bond6
```

### 4.1 VIP 与镜像仓库：两处容易被名字误导的实现

**VIP 不是 keepalived，是自研的 `vip-daemon`**（`amd64/vip/vip-daemon`，5.3MB Go 静态二进制，
x86-64）。部署方式是 systemd 服务，不是 k8s 对象：

```
ExecStart=/data/paas/vip/vip-daemon -config /data/paas/vip/config.json
User=root   Restart=always          # 必须 root，要改 ip addr
```

`config.json` 由 `install-vip-daemon.yml` 渲染，关键字段：

```json
{
  "primary_node_id": "<groups['k8s-master'][0]>",
  "interface": "<default_interface>",
  "vips": [{"interface": "bond6", "vip": "192.168.0.100/32"}],
  "peers": ["<其余所有节点>"],
  "peer_port": 5555, "http_port": 5556,
  "priority": 100 - (节点索引 * 10),
  "check_k8s_url": "https://127.0.0.1:6443/healthz",
  "ingress_url": "http://<vip>/gaop/user/login",
  "promote_script": "/data/paas/change_cluster_etcd.sh <host> <password>",
  "cron_script": "/data/paas/backup_gaop_mysql.sh",
  "cron_interval": "60s", "status_interval": "30s"
}
```

几个要点：

- **探活用的是业务登录页**（`ingress_url .../gaop/user/login`），不是单纯的 TCP 或 `/healthz`——
  这与 `更新说明.txt` v1.3.5 那条「VIP 统一检查 VIP/master 的 `/gaop/user/login` 最终 HTTP 200」一致；
- 节点优先级按 `host/hosts` 里的顺序递减（`100 - index*10`），第一个 master 最高；
- 切换时会调 `change_cluster_etcd.sh`（改 apiserver 的 etcd 指向），每 60s 跑一次
  `backup_gaop_mysql.sh`；
- **单节点时 `vips` 为 `[]`**，daemon 起来了但不做漂移——这是单 master 部署的正常形态。

**镜像仓库是 `registry:2.8.3`，不是 Harbor**：

| 项 | 值 |
| --- | --- |
| 清单 | `roles/registry/templates/registry.yml.j2` |
| 对象 | Deployment `docker-registry` + NodePort Service，都在 `kube-system` |
| 镜像 | `registry:2.8.3`（`amd64/images/registry/registry--2.8.3.tar.gz`） |
| 端口 | 容器 5000 → NodePort **1080** |
| 调度 | `nodeSelector: cur-etcd: etcd`（钉在 etcd 节点上） |
| 数据 | `/data/paas/registry` |
| 验收 | `curl -v http://<master>:1080/v2 -k`（任务里会重试 30 次） |

**Harbor 在这个包里不存在**——没有 harbor 目录、镜像、compose 文件。但你会看到一堆
`HARBOR_*`：`gaop-be.yaml` 的 `HARBOR_HOST=192.168.0.101:1080`、
`roles/docker/templates/daemon.json.j2` 的 insecure-registries、
以及诊断组件的 `harbor-compose-file: /data/paas/harbor/docker-compose.yml`。
这些是**历史命名遗留**，实际指向的都是 1080 上的 `registry:2.8.3`；
诊断组件里那个 compose 路径对应的文件根本不存在，属于死配置。

## 5. arm64：**脚本已支持，介质缺失**

这是本轮最重要的发现。`install-paas-prepare.yml` 里有**完整的 ARM 分支**
（`when: host_architecture == "arm"`），逐项列出需要从 `./arm64/` 取的文件。
但 `msop.tar.gz` 里**只有 `amd64/`**（`grep -c '^msop/arm64/'` = 0）。

也就是说：**不需要自己写 arm64 流程**，把 ARM 分支引用的文件补齐即可：

| 类别 | 需要的文件 |
| --- | --- |
| CNI 插件 | `arm64/cni-plugins-linux-arm-v1.3.0.tgz` |
| CRI | `arm64/crictl`、`arm64/cri-dockerd/cri-dockerd` |
| Docker | `arm64/docker/{docker,dockerd,docker-init,docker-proxy,containerd,containerd-shim-runc-v2,ctr,runc}` |
| Helm | `arm64/helm/helm` |
| Kubernetes | `arm64/kubernetes/server/bin/{kubeadm,kubelet,kubectl,kube-proxy,kube-apiserver,kube-controller-manager,kube-scheduler,kube-aggregator,apiextensions-apiserver,kube-log-runner,kubectl-convert,mounter}` |
| systemd | `arm64/config/*`（与 `amd64/config/*` 内容相同，可直接复制） |

`prepare-ansible.sh` 另有两处也需要 arm64 版本才能跑通：

- `arm64/ansible-env/podman-linux-arm64.tar.gz`
- `arm64/ansible-env/ansible-env-20250924-arm64.tar.gz`（+ `skopeo--v1.21.tar.gz`）

注意目录形状不要照抄 amd64：amd64 分支用的是 `amd64/tools/v1.27.3.tar.gz`（压缩包），
ARM 分支用的是**解开的** `arm64/kubernetes/server/bin/*`（单文件），两者布局不同。

> 结论：厂商有 arm64 版安装介质，只是这个 tar 没带。要 arm64 安装包，最快的路不是
> 重写流程，而是**向厂商索取 `arm64/` 目录**（或从他们的 arm64 安装包里取），
> 然后按上表与 amd64 逐项对齐。

## 6. 这套流程里值得注意的几处（不是 bug，但会咬人）

| 项 | 位置 | 说明 |
| --- | --- | --- |
| inventory 明文口令 | `host/hosts` 的 `ansible_password="glch.722"` | 分发前必须改；也是「拿到包就能登所有节点」的风险点 |
| kubeadm 引导 token 硬编码 | `kubeadm_init.yaml` 的 `token: abcdef.0123456789abcdef`、`ttl: 24h` | 与 static-token 是两回事：前者用于 join，后者用于 apiserver 鉴权 |
| `--ignore-preflight-errors=all` | `roles/kubeadm/tasks/main.yaml` | 预检失败也继续，swap/端口冲突不会拦住安装 |
| K8s 1.27.3 | `kubeadm_init.yaml` | 已于 2024 年停止维护；自研栈是 1.31，混用需对齐版本 |
| 静态 token 认证 | `install-paas-basic-server.yml` | 直接 `lineinfile` 改 `/etc/kubernetes/manifests/kube-apiserver.yaml`，属于「改静态 Pod 清单」，升级后要复查 |
| 单 master | `host/hosts` | worker 与 VIP 都是注释状态；多 master 需放开并配 `vip-daemon`（`install-vip-daemon.yml`） |
| `PODMAN_ARGS='--runtime crun'` | `prepare-ansible.sh` | 依赖包内 crun；若宿主机已有 runc 会被删掉（`rm -f /usr/local/bin/runc`） |

## 7. 与自研部署包的关系

| | 厂商 `msop.tar.gz` | 我们的 `paas-deploy` |
| --- | --- | --- |
| 范围 | 裸机 → K8s 集群 → 中间件 → 业务全栈 | 只替换应用层（`paas-core` + `paas-web`） |
| 前提 | 裸机 + root + 网络 | 已有集群 + MySQL/Redis |
| 装机方式 | Ansible（podman 容器内跑） | bash 脚本 + kubectl |
| 版本 | K8s 1.27.3 / gaop v1.3.5 | K8s 1.31.9 目标集群 / 自研应用 |
| arm64 | 脚本支持、介质缺失 | 镜像全 arm64、自带 kubectl |

两者**不是替代关系**：厂商包负责「把机器变成一套能跑 MSOP 的 K8s」，我们负责
「在这套 K8s 上把 gaop-be / gaop-web 换成自研实现」。若客户要一份「arm64 全栈交付」，
= 厂商 `arm64/` 介质补齐 + 我们的 `paas-deploy`；前者我们目前不提供。
