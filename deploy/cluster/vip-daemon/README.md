# vip-daemon（arm64 等价实现）

厂商只提供 **amd64** 的 VIP 守护进程（`msop/amd64/vip/vip-daemon`，Go 静态二进制、`-s -w` 剥离符号），
而测试/交付集群是 **arm64**，无法运行。它缺失时 `gaop-be` 的 `GET /v1/pub/vip` 会因为
feign 连不上 `http://<VIP_HOST>:5556/api/vips` 而整条 500（虚拟IP页面不可用）。

本目录是**同接口的 arm64 实现**，只读优先。

## 为什么不是"假接口"

单节点集群没有 HA，厂商自己的配置模板 `config.json.j2` 在 `node_list|length == 1` 时
也是 `vips: []`。所以返回空列表是**系统真实状态**，不是编造数据。

写操作（新增/删除 VIP）会真实修改宿主机网卡，单节点没有对端可漂移，本实现直接返回
**501 + 原因**，不做"点了没反应"的假入口。

## 接口（与 recovered 的 `VipDaemonApi.java` 对齐）

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/vips` | 返回 VIP 数组（单节点为 `[]`），元素 `{interface, vip}` |
| GET | `/api/interfaces` | 真实网卡：`{name, mac, flags, addresses[]}` |
| POST | `/api/vips`、`/api/vips/add`、`/api/vips/delete` | 501 Not Supported |
| GET | `/health` | 探针用 |

> `InterfaceInfo.index` 在 Java 侧是 `@JsonIgnore`（gaop-be 不会把它透出给前端），本实现未输出该字段。

## 构建（本机交叉编译，不需要 Docker Hub）

```bash
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o /tmp/vip-daemon-arm64 .

# 打成 docker 镜像（docker import 不依赖基础镜像，天然支持离线）
rm -rf /tmp/viproot && mkdir -p /tmp/viproot && cp /tmp/vip-daemon-arm64 /tmp/viproot/vip-daemon
tar -C /tmp/viproot -cf /tmp/viprootfs.tar vip-daemon
docker import --platform linux/arm64 \
  -c 'ENTRYPOINT ["/vip-daemon"]' -c 'CMD ["-config","/data/paas/vip/config.json"]' \
  /tmp/viprootfs.tar vip-daemon:arm64-local
docker save vip-daemon:arm64-local -o /tmp/vip-daemon-img.tar      # 约 5.3 MB
```

## 导入气隙集群

节点是 docker 运行时、socket 在 `/run/docker.sock`。用一个临时 Pod 挂 socket 导入：

```yaml
# 关键两点：hostPath 用 /run/docker.sock；必须 runAsUser: 0
#   非 root 读不了 root:root 660 的 socket，curl 会报 couldn't connect (exit 7)
spec:
  containers:
    - name: loader
      image: docker.1ms.run/curlimages/curl:latest
      command: ["sleep", "1800"]
      securityContext: {runAsUser: 0, runAsGroup: 0}
      volumeMounts: [{name: sock, mountPath: /run/docker.sock}]
  volumes:
    - name: sock
      hostPath: {path: /run/docker.sock}
```

```bash
kubectl apply -f /tmp/img-loader.yaml
kubectl cp /tmp/vip-daemon-img.tar gaop-namespace/img-loader:/tmp/img.tar
kubectl exec -n gaop-namespace img-loader -- sh -c \
  'curl -s --unix-socket /run/docker.sock -X POST -H "Content-Type: application/x-tar" \
   --data-binary @/tmp/img.tar http://localhost/images/load'
kubectl delete pod img-loader -n gaop-namespace --force --grace-period=0   # 用完即删
```

## 部署

```bash
kubectl apply -f k8s.yaml
# DaemonSet 必须 hostNetwork（要读宿主机网卡），ConfigMap 给单节点 vips: []
```

然后把后端指过来：

```bash
kubectl -n gaop-namespace patch deploy gaop-be --type=strategic \
  -p '{"spec":{"template":{"spec":{"containers":[{"name":"gaop-be","env":[{"name":"VIP_HOST","value":"vip-daemon"}]}]}}}}'
```

配置项来源：`vip.host = ${VIP_HOST:${SSH_HOST:localhost}}`、`vip.port=5556`（application.yml）。
**注意必须显式设 `VIP_HOST`**——只删旧值会退回 `SSH_HOST`（本集群曾是 `192.168.1.52`）。

## 验证

```bash
curl -s http://172.16.209.25:5556/api/vips          # → []
curl -s http://172.16.209.25:5556/api/interfaces    # → 真实网卡
# 后端侧（走 kubectl proxy 或网关）
curl -s -H "Cookie: gateway-token=$TOKEN" \
  "http://127.0.0.1:18001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy/v1/pub/vip"
# → {"msg":"OK","code":0,"data":[]}
```

## 边界与后续

- **写操作未实现**：真的加/删 VIP 会改网络，单节点无漂移对端，风险大于收益。需要时再实现
  `ip addr add/del` + 主备选举（对应厂商的 `isHigherNode`/`candidateNodeNames`/`detectCurEtcd`）。
- **无 HA 能力**：不监听 5555 peer 口、不做 etcd 角色提升、不做 MySQL 备份 cron。
- 集群重建后镜像会丢（`vip-daemon:arm64-local` 未进任何 registry），需重新走上面的导入流程。

## 怎么保证与厂商一致：拿厂商二进制做参照物

这个组件与其它模块最大的不同是——**厂商的实现可以跑起来**。虽然只有 amd64，但 Docker Desktop
的 amd64 模拟足够跑它，于是"一致"不靠猜，靠**同一请求对拉响应**。

```bash
# 1) 把厂商二进制 + 配置打成 amd64 镜像（tar 必须保留 755，否则 exec: permission denied）
cp /Users/csm/Desktop/PaaS/msop/amd64/vip/vip-daemon /tmp/vipsrc/ && chmod 755 /tmp/vipsrc/vip-daemon
cd /tmp/vipsrc && tar -cf /tmp/vipsrc.tar vip-daemon config.json
docker import --platform linux/amd64 -c 'ENTRYPOINT ["/vip-daemon"]' \
  -c 'CMD ["-config","/etc/vip/config.json"]' /tmp/vipsrc.tar vip-daemon-vendor:amd64

# 2) 起一个，便于随时对照
docker run -d --name vipd-vendor --platform linux/amd64 -p 15557:5556 \
  -v /tmp/vipsrc:/etc/vip vip-daemon-vendor:amd64

# 3) 对拉（curl 到 15557 就是厂商行为，到 5556 是我们的）
curl -s localhost:15557/api/vips; curl -s localhost:5556/api/vips
```

### 第一次对拉就发现 5 处不一致（已全部修正）

厂商 `vip-daemon v1.0.9-20260731-link-health` 的实测行为 vs 我们的初版：

| 项 | 厂商【实】 | 我们初版 | 处置 |
|---|---|---|---|
| `InterfaceInfo.index` | **输出** `{"index":1,...}` | 省略（误信 Java 的 `@JsonIgnore`） | 补上；`@JsonIgnore` 只影响 gaop-be→前端，不影响 daemon→gaop-be |
| 网卡过滤 | **不过滤**，`lo`/`cali*` 原样返回 | 按前缀过滤 | 去掉过滤；过滤在 `PubVipController.interfaces()`（用 `vip.ignore-prefix-list`） |
| `addresses` 为空 | **`null`** | `[]` | 用 nil 切片 |
| `flags` 无标志 | **`"0"`** | `""` | 映射为 `"0"` |
| `/api/config/sync`、`/api/configmap` | **404**（strings 里的残留，未注册路由） | 我们实现了 501 | 删除路由，保持同样 404 |

### 有意保留的偏离（B 类，必须在 deviations 台账里）

| 项 | 厂商【实】 | 我们 | 理由 |
|---|---|---|---|
| `POST /api/vips/add|delete` | **200 + 纯文本 `OK`**，先写内存并回写 `config.json`，再异步 `ip addr`/`nmcli` 绑定，失败也返回 200 | 501 + 原因 | 单节点无漂移对端，真改网络风险大于收益；不做"假成功" |
| `GET /health` | **404** | 200 | kubelet 探针需要；对上游无影响 |

厂商实现还依赖 `ip`、`nmcli`、`kubectl` 三个外部命令（容器里没有就直接失败），
并且 **`vips` 状态持久化在 `config.json` 文件里**（重启后恢复）——这是它"配置文件既是输入也是输出"的设计。
我们不实现写，因此也不做回写。

### 一个连带发现：网卡过滤的前缀表在 gaop-be，不在 daemon

把 daemon 改成"原样返回"后，`/v1/pub/vip/interfaces` 冒出了 **38 个网卡**（37 个 `cali*` + `tunl0`），
因为 `application.yml` 的默认前缀表只有 `lo/docker/kube-/flannel/usb/virbr/cni/veth`，**没有 cali/tunl**。
给后端补环境变量即可（Spring 的 `List` 支持逗号分隔绑定）：

```bash
kubectl -n gaop-namespace patch deploy gaop-be --type=strategic -p \
 '{"spec":{"template":{"spec":{"containers":[{"name":"gaop-be","env":[
   {"name":"VIP_IGNORE_PREFIX_LIST","value":"lo,docker,kube-,flannel,usb,virbr,cni,veth,cali,tunl"}]}]}}}}'
```

补完只剩下真实业务网卡（本集群 `ens160`）。

### 回归方式

改了 `main.go` 之后，至少跑这三条并对比厂商侧：

```bash
curl -s localhost:5556/api/vips            # 契约：裸数组，空为 []
curl -s localhost:5556/api/interfaces      # 契约：元素含 index/name/mac/flags/addresses，空地址为 null
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:5556/api/vips/add   # 行为：501（有意偏离）
```
