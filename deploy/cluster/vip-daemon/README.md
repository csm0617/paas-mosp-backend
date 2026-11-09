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
