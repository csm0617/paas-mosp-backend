# 厂商集群部署流程：还原与缺口

对象：`msop-v1.3.4-20260416.tar.gz`（2.8G，解压 3.1G / 514 个文件，纯 amd64）。

**结论先说**：这个包里**没有**「从 0 装一套集群」的可执行流程。它是**节点侧介质包 +
「加节点」入口**：素材大约有三分之一（镜像、二进制、systemd unit、Helm chart），
流程是 0（主 playbook 不在包内）。

本文把包里**每一个**脚本类文件都列出来并给判断，再用 `include` 列表把完整流水线还原成
21 步，逐步标注素材是否在包内——这样「缺什么」是清单，不是印象。

## 1. 包里全部脚本类文件（27 个，已逐个读完）

| 文件 | 行数/大小 | 作用 | 属于哪一步 |
| --- | --- | --- | --- |
| `!安装和使用注意事项.txt` | 4 行 | 关 swap、配网关/DNS、设 root 密码、单默认网关 | 前置要求 |
| `add-node-prepare.yml` | 8 行 | 步骤 1/4 入口 | 环境准备 |
| `add-node-docker.yml` | 11 行 | 步骤 2/4 入口 | 运行时 |
| `add-node-kubernetes.yml` | 96 行 | 步骤 3/4 入口 | K8s 主体 |
| `add-node-minio-replicate.yml` | 55 行 | 步骤 4/4 入口 | 对象存储 |
| `amd64/config/docker.service` | 33 行 | dockerd，`--containerd=/run/containerd/containerd.sock` | 运行时 |
| `amd64/config/docker.socket` | 11 行 | `/var/run/docker.sock` | 运行时 |
| `amd64/config/containerd.service` | 39 行 | containerd，`modprobe overlay` 前置 | 运行时 |
| `amd64/config/cri-docker.service` | 44 行 | cri-dockerd，`--pod-infra-container-image=pause:3.9` | 运行时 |
| `amd64/config/cri-docker.socket` | 11 行 | `cri-dockerd.sock` | 运行时 |
| `amd64/config/kubelet.service` | 17 行 | kubelet（`ExecStart=/usr/bin/kubelet`） | K8s 节点 |
| `amd64/config/kubelet.service.d/10-kubeadm.conf` | 12 行 | kubeadm 的 kubelet drop-in（bootstrap-kubeconfig + config.yaml） | K8s 节点 |
| `amd64/executable/etcd/etcd.service` | 10 行 | 外部 etcd，`EnvironmentFile=/etc/etcd/etcd.conf` | 控制面存储 |
| `amd64/executable/etcd/node-recovery.sh` | 38 行 | 宕机节点恢复后，把 apiserver 的 `--etcd-servers` 改回指定 etcd 并重启 kubelet | 故障恢复 |
| `amd64/executable/minio/minio.service` | 12 行 | MinIO 单机服务 | 对象存储 |
| `amd64/diagnostic/clearPod.sh` | 1 行 | `kubectl delete po --field-selector=status.phase=Failed --all-namespace` | 运维 |
| `amd64/diagnostic/templates/gaop-diagnostic.service.j2` | 19 行 | 诊断服务 systemd（`-Dserver.port=55555`） | 运维 |
| `amd64/diagnostic/templates/gaop-diagnostic.j2` | 8 行 | logrotate 配置 | 运维 |
| `amd64/diagnostic/templates/application.yml.j2` | 41 行 | 诊断服务配置（SQLite、hosts、harbor ip） | 运维 |
| `amd64/diagnostic/templates/agent_daemonset.yml.j2` | 81 行 | debug-agent DaemonSet（`extensions/v1beta1`，**已废弃 API**） | 运维 |
| `amd64/diagnostic/templates/agent_daemonset_high.yml.j2` | 81 行 | 同上，`apps/v1` + control-plane 容忍 | 运维 |
| `.gitattributes` / `.gitignore` | — | LFS 与忽略规则（暴露了被排除的目录） | 元数据 |
| `amd64/ansible-env/*`（3 个） | 167MB | ansible + podman + skopeo + crun 的运行环境（OCI 镜像） | 执行器 |

两个 `.sh` 的完整逻辑已经读完：`clearPod.sh` 只有一行 kubectl 删失败 Pod；
`node-recovery.sh` 是 etcd 切换 + 重启 kubelet，属于事后修复，不是安装。

`ansible-env` 我也解到 layer 层验证过：里面只有 `usr/local/bin/ansible-playbook` 与
ansible 本体，**没有任何业务 playbook**。

## 2. 四个 playbook 的真实身份

它们不是「第 1/4 步安装、第 2/4 步安装」这种从零流程，而是**往已有集群加节点的
四个阶段**（文件头注释就写了「添加节点执行的脚本，基于 `install-k8s-all.yml` 修改」）。

`add-node-kubernetes.yml` 的实际动作顺序（去掉注释后）：

```
stop vip（worker）→ include install-paas-kubernetes.yml（kubeadm join）
→ 删 control-plane 污点 → start vip → flannel → etcd 备份 → minio
→ install-other → cron 清 Pod → diagnostic → gpu → k9s
```

也就是说：**它的前提是集群已经存在**（有 vip、有 master、有 etcd 集群），
新节点只做 join 与外挂组件。

## 3. 被 include 但不在包内的文件：21 个

我把 4 个 playbook 的 `include` 全部解析并逐个查了文件是否存在：

| playbook | 引用但缺失 |
| --- | --- |
| `add-node-prepare.yml` | `precheck-system.yml`、`ssh.yml`、`install-paas-prepare.yml`、`site-ntp.yml` |
| `add-node-docker.yml` | `install-paas-cni.yml`、`install-paas-docker.yml`、`install-paas-cri-docker.yml`、`install-paas-crictl.yml`、`install-mydumper.yaml` |
| `add-node-kubernetes.yml` | `install-paas-etcd.yml`、`install-vip-daemon.yml`、`install-paas-kubernetes.yml`、`install-paas-networks-flannel.yml`、`site-etcd-backup.yml`、`install-minio.yml`、`install-other.yml`、`install-machine-code.yml`、`cron-del-completed-pod.yaml`、`install-diagnostic.yaml`、`install-gpu.yml`、`install-k9s.yaml` |
| `add-node-minio-replicate.yml` | 无（全内联） |

**21 个全部缺失**。同时包里也没有 `roles/`、`group_vars/`、`host_vars/`、`inventory`、
`ansible.cfg`——整个 Ansible 工程只剩 4 个入口文件。

`.gitignore` 的注释还留了两条线索：
`00-prepare.sh`（含 `prepare_download_gpu()`）与 `00-prepare-offline.sh`——真正的安装
入口应该叫这两个名字，它们也不在包里。

## 4. 还原出的完整流水线（21 步）与素材对照

`include` 的命名把厂商的完整流水线暴露得很清楚。下面按依赖顺序排，并标注**每一步的
素材是否在这个 tar 内**：

| # | 步骤（由 include 名还原） | 干什么 | 素材在包内？ |
| --- | --- | --- | --- |
| 1 | `precheck-system.yml` | 系统检查（swap/内核/端口/时间） | ❌ |
| 2 | `ssh.yml` | root 免密 | ❌ |
| 3 | `install-paas-prepare.yml` | 建目录、下发参数 | ❌ |
| 4 | `site-ntp.yml` | 内网授时 | ❌ |
| 5 | `install-paas-cni.yml` | CNI 前置（内核模块/网桥） | ❌（flannel 镜像 ✅） |
| 6 | `install-paas-docker.yml` | 装 docker | systemd ✅ / 二进制 ❌ |
| 7 | `install-paas-cri-docker.yml` | 装 cri-dockerd | systemd ✅ / 二进制 ❌ |
| 8 | `install-paas-crictl.yml` | 装 crictl | ❌ |
| 9 | `install-mydumper.yaml` | MySQL 逻辑备份工具 | ❌ |
| 10 | `install-paas-etcd.yml` | 外部 etcd 集群 | 二进制 ✅ + unit ✅ / playbook ❌ |
| 11 | `install-vip-daemon.yml` | keepalived VIP（多 master） | ❌ |
| 12 | `install-paas-kubernetes.yml` | kubeadm init / join | kubelet unit ✅ + 控制面镜像 ✅ / **kubeadm 二进制 ❌** |
| 13 | `install-paas-networks-flannel.yml` | 部署 flannel | 镜像 ✅ / playbook ❌ |
| 14 | `site-etcd-backup.yml` | etcd 定时备份 | ❌ |
| 15 | `install-minio.yml` | MinIO | 二进制 ✅ + unit ✅ |
| 16 | `install-other.yml` | 其它（registry、kafka-ui 等镜像） | 镜像 ✅ / playbook ❌ |
| 17 | `install-machine-code.yml` | 机器码（授权） | ❌ |
| 18 | `cron-del-completed-pod.yaml` | 清 Completed Pod 的 cron | ❌ |
| 19 | `install-diagnostic.yaml` | 诊断组件（jar + j2 模板） | jar ✅ + 模板 ✅ / playbook ❌ |
| 20 | `install-gpu.yml` | GPU 插件（按 label） | ❌（`.gitignore` 注明走 `00-prepare.sh` 下载） |
| 21 | `install-k9s.yaml` | k9s 运维终端 | 二进制 ✅ / playbook ❌ |

**素材覆盖约 1/3，流程覆盖 0**。缺的三类：

1. **编排层**：21 个 playbook + 2 个 `00-prepare*.sh` 入口 + inventory；
2. **二进制层**：`kubeadm`、`kubelet`、`kubectl`、`kube-proxy`、`crictl`、`cri-dockerd`、
   `dockerd`、`containerd`、`runc`（systemd unit 里引用的 `/usr/bin/*` 一个都不在包里）；
3. **业务层**：`k8s-yaml/`（被 `.gitignore` 排除）、`deploy-fdfs/`（注明需单独下载）。

## 5. 那这个包到底能干什么

| 场景 | 能做吗 | 依据 |
| --- | --- | --- |
| 往已有同名集群加一个 worker | ⚠️ 只到「入口能跑」为止 | 4 个 playbook 在，但 include 的 21 个不在，实际执行会 `Unable to retrieve file contents` |
| 导入镜像（业务 + 中间件 + K8s 组件） | ✅ | `basic_images/`、`kubernetes_images.tar`、`openebs.tar`、`flannel`、`ingress-nginx` |
| 复用节点 systemd 配置 | ✅ | `amd64/config/*.service` 与架构无关，可直接改用 |
| 复用 etcd/minio 二进制与 unit | ⚠️ 需换 arm64 | 现为 x86-64 |
| 复用 Helm chart（ingress-nginx/prometheus/loki） | ✅ | `amd64/helm/` |
| 从零装一套集群 | ❌ | 缺编排层与二进制层 |

## 6. 要拿到「从 0」的流程，只有两条路

**路 A：向厂商要主安装包**。这个 tar 里的 `.gitattributes` 用了 Git LFS
（`amd64/** filter=lfs`），说明它本来是一个 Git 仓库的检出。4 个 `add-node-*.yml`
注释里提到的 `install-k8s-all.yml`、`.gitignore` 注释里的 `00-prepare.sh`
都是同一仓库的文件——**主安装包是另一个 tar**，不在这次给的这个里。

**路 B：自建 arm64 流程，不依赖缺失的 playbook**。最小依赖集：

| 组件 | 来源 | 备注 |
| --- | --- | --- |
| `kubeadm`/`kubelet`/`kubectl` | `dl.k8s.io/release/v1.31.9/bin/linux/arm64/` | 静态二进制，可直接进包 |
| 控制面镜像（7 个） | `registry.k8s.io` 的 v1.31.x 多架构 | 包内现有的是 v1.27.3 amd64，只能作参照 |
| `containerd` + `runc` | 发行版包或官方 tar（arm64） | 若沿用厂商的 docker + cri-dockerd，也要换 arm64 |
| systemd unit | 直接复用 `amd64/config/*.service` | 与架构无关 |
| CNI | flannel 的 arm64 镜像 | 清单自己写 |
| `kubeadm-config.yaml` | 自己写 | 外部 etcd / VIP 是厂商拓扑，单节点可省 |

这条路的工作量集中在「镜像 arm64 化 + kubeadm 配置」；**不必**去还原那 21 个 playbook
——它们做的事（装 docker、装 kubelet、join、装 flannel、装 minio）用 kubeadm 官方流程
可以更短地做到。

## 7. 与自研部署包的关系

`paas-deploy` 只装应用层，**不接管集群**。这份文档的作用是：

- 现场若说「集群我们自己装」，可以直接把本文 §4 的 21 步当验收清单的对照；
- 现场若问「你们能不能把集群也装了」，答案是「能，但不是从这个包改，而是走 kubeadm
  官方离线流程」——因为厂商包里缺的正是那套编排；
- 不要再花时间在 `msop-v1.3.4` 里找主 playbook：`include` 已解析完，21/21 缺失。
