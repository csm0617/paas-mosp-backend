# 167 · 厂商前端 × 后端接口连通性体检（196 端点 / 88 GET 实测）

> 时间：2026-11-09 21:20–21:50
> 对象：测试集群 `172.16.209.170`，厂商前端 `/gaop/`（recovered-web 产物）+ 厂商后端
> `gaop-be:V1.3.4-20260415-1857-arm64`（pod `gaop-be-56ccf9c589-8626c`，内存 942Mi / limit 1Gi）
> 方法：`admin/admin123` 经 `POST /v1/pub/login` 拿 `gateway-token`（Cookie），
> 按 `api-catalogue.json` 登记的 **88 个 GET** 逐条重放，参数按各端点的 `query` 定义填真实值；
> 失败项再回查 `gaop-be` 日志与反编译源码定位。
> 证据等级：【实】= 本次实测；【码】= 反编译代码事实；【推】= 推断。

## 一、一句话结论

**没有"接口不存在"这类缺口**：厂商前端调用的 196 个端点，在厂商后端 493 条映射里**全部有对应路由**
（方法+路径逐条匹配，0 缺口）【实】。当前调不通的 19 条，**全部是运行时依赖问题或厂商代码健壮性缺陷**，
按根因分四类：**Prometheus 被探针反复杀、下游地址仍指厂商老环境、docker socket 未挂、厂商 NPE**。

88 个 GET 实测：修复前 **69 通过（78%）/ 19 失败**；修完 Prometheus 后 **71 通过（81%）/ 17 失败**（详见 §九）。
第一轮裸打是 68/20，多出的那 1 条是 `/audit` —— 它的 `start/end` 是 `Long` 毫秒
（与 dashboards 的 `Date` 同名字段不同类型），改传毫秒后 200，属探针参数错误而非接口故障。

> 复跑：`python3 tools/probe-vendor-api.py`（需先 `kubectl proxy --port=18001`；详见 §八）。

## 二、失败清单（19 条，全部实测）

| # | 接口 | 现象【实】 | 根因 | 类别 |
|---|---|---|---|---|
| 1 | `GET /v1/pub/nodeUsage` | 500「获取内存状态失败」 | Prometheus 不可用（`SYSTEM_MONITORING=172.16.209.25:30003` 间歇挂） | 组件 |
| 2 | `GET /v1/pub/nodeMonitor` | 400 `k8s.get.cluster.resource.info.failed` | 同上 | 组件 |
| 3 | `GET /api/v2/dashboards/cockpit` | 500 NPE `DashboardsService.cockpit:548` | **Deployment 的 `resources.limits` 只有 memory 没有 cpu** ⇒ 字节码偏移 307 处 `limits.get("cpu").getNumber()` 对 null 调方法（见 §3.4） | 清单 |
| 4 | `GET /v1/service/gaop` | 500 NPE `ServiceController.getGaopServiceList:1961` | 厂商 NPE（应用列表装配，`type=1/2` 均复现） | 厂商 NPE |
| 5 | `GET /v1/pub/vip` | 500 feign `connect timed out ... http://192.168.1.52:5556/api/vips` | VIP daemon 未部署，且地址是厂商老环境 `192.168.1.52` | 地址 |
| 6 | `GET /v1/pub/vip/interfaces` | 500 同上 `/api/interfaces` | 同上 | 地址 |
| 7 | `GET /v1/database` | 20s 超时 | third 数据源 `jdbc:mysql://192.168.1.166:3306/mysql` 不可达，Druid 建连反复失败 | 地址 |
| 8 | `GET /v1/database/user` | 20s 超时 | 同上 | 地址 |
| 9 | `GET /v1/database/users` | 20s 超时 | 同上 | 地址 |
| 10 | `GET /v1/database/table_size` | 20s 超时 | 同上 | 地址 |
| 11 | `GET /v1/database/initSql/download` | 400「未获取到数据库 gaop 的初始化文件」 | 同上（库都连不上，自然没有文件） | 地址 |
| 12 | `GET /v1/image/image_env` | 500 `native connect() failed : No such file or directory` | `docker.host=unix:///var/run/docker.sock`，pod 内**没挂** socket | 环境 |
| 13 | `GET /v1/image/repositories` | 20s 超时 | Harbor（`harbor:30002`）不可达，DNS 假连通后读响应挂起 | 环境 |
| 14 | `GET /v1/image/imageList` | 20s 超时 | 同上 | 环境 |
| 15 | `GET /v1/image/tags` | 20s 超时 | 同上 | 环境 |
| 16 | `GET /v1/image/tagInfo` | 20s 超时 | 同上 | 环境 |
| 17 | `GET /v1/image/expose` | 20s 超时 | 同上 | 环境 |
| 18 | `GET /v1/service/imageStorage` | 20s 超时 | 同上 | 环境 |
| 19 | `GET /v1/pub/unify/etcd` | 400「文件不存在：/data/paas/node-recovery/node-recovery.sh」 | 宿主机缺该脚本（厂商安装包 `amd64/executable/etcd/` 内有） | 环境 |

## 三、四条根因的细节

### 3.1 Prometheus 不是"没装"，是"每两分钟被杀一次"（本轮最重要的更正）

`namespace prometheus` 组件齐全（prometheus / node-exporter / kube-state-metrics / cadvisor 全部 Running），
但 `prometheus-748758c5bd-tgclz` **CrashLoopBackOff，已重启 219 次**【实】。

日志显示它**每次都能正常启动**（TSDB replay 22s → `Server is ready to receive web requests`），
2~3 分钟后收到 `SIGTERM, exiting gracefully`，退出码 0（`Reason: Completed`）。真正的杀手指向探针：

```
Liveness:  http-get http://:9090/-/healthy delay=30s timeout=1s period=10s #failure=3
Readiness: http-get http://:9090/-/ready    delay=10s timeout=1s period=10s #failure=3
Warning  Unhealthy  Liveness probe failed: context deadline exceeded (Client.Timeout exceeded while awaiting headers)
Warning  BackOff    (x3927 over 23h)
```

**`timeoutSeconds=1` 在 2 核、CPU 已跑满的单节点上不现实**：`/-/healthy` 只要偶发超过 1 秒，
连续 3 次就触发重启。重启期间（含 22s WAL replay）`30003` 完全拒绝连接，
于是所有走 Prometheus 的接口在"可用/不可用"之间来回跳——这解释了为什么同一接口我先后测到
200 和 500（`/api/v2/dashboards/etcd`、`apiServer` 一次 200 一次超时）【实】。

**修法**：`timeoutSeconds` 提到 5~10，或去掉 liveness 只留 readiness；也可以在探针上加 `startupProbe`。
这是**部署清单缺陷**，不是厂商代码问题。

### 3.2 metrics-server 其实可用——findings/100 的 P0 第 1、2 项已经不再成立

`kubectl get --raw /apis/metrics.k8s.io/v1beta1/nodes` 返回完整 `NodeMetricsList`【实】；
`GET /cloud/summary`、`GET /cloud/nodesummary` **本次均 200**【实】。
100 号文档把这两条列为"缺 metrics-server ⇒ 500"，现已被环境演进推翻（metrics-server 装在
`kube-system`，走聚合 API，`kubectl get all -n metrics-server` 看不到）。

### 3.3 下游地址仍指厂商老环境（且和 100 号记录的数字不同）

实测从 `gaop-be` 日志抓到的真实连接串【实】：

| 下游 | 实际地址 | 出处 |
|---|---|---|
| 被管理 MySQL | `jdbc:mysql://192.168.1.166:3306/mysql` | Druid `create connection SQLException, state 08S01` |
| VIP daemon | `http://192.168.1.52:5556` | feign `RetryableException` |
| Harbor | `harbor:30002`（DNS 假连通） | K3s/Harbor 客户端超时 |

100 号文档记的是 `192.168.1.171:3306`，实际是 **`.166`**，以日志为准。
这些变量（`MYSQL_HOST`/`VIP_HOST`/`HARBOR_HOST`/`FDFS_TRACKERS`）在当前 Deployment 里**没有声明**，
走的是镜像内 `application.yml` 默认值 ⇒ 想纠偏得显式加 env，光"删掉旧 env"不会变成本集群地址。

**已知可用的对照**：`SYSTEM_MONITORING`、`LOG_MONITORING` 已被改成 `172.16.209.25`，
Loki `30008` 实测 `/ready` 200【实】——说明纠偏路径是通的，只是还没覆盖到这四个变量。

### 3.4 厂商 NPE 两处（A 类，不复制）

- **`DashboardsService.cockpit`【码+实】**：`javap -l` 查到 `line 548` 对应字节码偏移 307，
  该处是 `limits.get("cpu")` → `checkcast Quantity` → `getNumber()`：

  ```
  300: aload 24            // V1ResourceRequirements
  302: invokevirtual getLimits()Ljava/util/Map;
  305: astore 25
  307: aload 25            // ← line 548
  309: ldc "cpu"
  311: invokeinterface Map.get
  316: checkcast Quantity
  319: invokevirtual Quantity.getNumber()   // ← cpu 缺失时 NPE
  ```

  本集群 **10 个 Deployment 的 `resources.limits` 全部只有 `memory`、没有 `cpu`**【实】，
  于是第一个进入循环的工作负载就抛 NPE。厂商代码假设 limits 里 cpu/memory 必成对出现。
  **修法**：给工作负载补 `limits.cpu`（只影响上限，不增加调度占用）。

  > 更正：本文初稿把它归因成"Prometheus 无 pod 指标 ⇒ `pm.getMetrics()` 为 null"，
  > **是错的**。`convertPrometheusData` 对 null 入参返回**空 list** 而不是 null（源码 943 行），
  > 所以 metrics 永远不会是 null；真正的 NPE 在资源上限那句。
- `ServiceController.getGaopServiceList:1961`【码】：应用服务列表装配 NPE，`type=1/2` 都复现。
  与 findings/166 的 `allService` NPE **不是同一处**（166 那条靠数字端口已恢复，`/v1/service` 现在 200【实】）。

## 四、与 findings/100 的差异（哪些已经好了）

| 100 号当时的结论 | 现在【实】 | 说明 |
|---|---|---|
| `/cloud/summary` 500（缺 metrics-server） | **200** | metrics-server 已可用 |
| `/cloud/nodesummary` 500 | **200** | 同上 |
| `/v1/service` 500（TenX NPE） | **200** | findings/166 改数字端口后恢复 |
| `/v1/lang/detail` 500 | **200** | 原来缺 `lang` 参数（本次带参） |
| `/v1/service/serviceInfoByNode` 400 | **200** | 原来缺 `nodeName` 参数 |
| `/v1/config_map` 列表恒空 | **200** | 契约结构已按 `items` 修（M05 轮） |
| `/v1/pub/existNodeName` 400 | **200** | 原来缺 `name` 参数 |
| `/v1/pub/nodeUsage` 500（归因 Prometheus） | 仍 500 | 归因正确，但真因是探针自杀 |

## 五、探针自身的四个坑（会伪造"接口坏了"）

本次第一轮裸打出现大量假失败，逐条记下来，避免下一轮重复：

1. **缺 `@RequestParam` ⇒ 厂商回 500 而不是 400**。25 条"系统错误"实际是
   `MissingServletRequestParameterException`，只要按 `api-catalogue.json` 的 `query` 补齐就变 200。
2. **`Date` 参数要 ISO8601 带时区**：`2026-11-09T20:00:00.000+08:00`。
   传 `2026-11-09 20:00:00` 报 `malformed at " 20:00:00"`，传毫秒报 `malformed at "5799"`。
3. **`/audit` 的 start/end 是 `Long` 毫秒**，与 dashboards 的 `Date` 不同（同名字段两种类型）。
4. **镜像名必须 `project/name`**：`K3sImageServiceImpl.getImageTagList:385` 对不含 `/` 的名字
   直接抛「镜像名错误」。本次用 `gaop/gaop-be` 才走到真实分支。

## 六、建议动作（按投入产出排序）

| 优先级 | 动作 | 解决 |
|---|---|---|
| P0 ✅ | 把 Prometheus 探针 `timeoutSeconds` 1 → 5，并加 `GOMEMLIMIT=850MiB`、retention 降到 2h（**已执行**，见 §九） | 第 1、2 条，以及所有监控页的间歇失败 |
| P1 | 给 `gaop-be` 显式注入 `MYSQL_HOST=<本集群 mysql>`、`VIP_HOST`、`HARBOR_HOST`，或确认这些下游"本就不部署" | 第 5~11 条 |
| P1 ✅ | 给 11 个 Deployment 补 `limits.cpu`（**已执行**，见 §十；NPE 消除，但 cockpit 仍卡 Harbor） | 第 3 条的 NPE 部分 |
| P2 | 把宿主机 `/var/run/docker.sock` 挂进 `gaop-be`（或确认镜像模块改走 Harbor API） | 第 12 条 |
| P2 | `node-recovery.sh` 放到节点 `/data/paas/node-recovery/` | 第 19 条 |
| —— | 第 4 条、第 3 条的 NPE 属厂商缺陷（A 类），重写版不复刻，已记录 | —— |
| —— | Harbor 与 VIP daemon 不在安装包内，属外部前提；若本阶段不验收镜像/虚拟IP 两页，可不部署 | 第 13~18 条 |

## 七、616 口径复核（静态）

- 厂商前端 196 调用 ↔ 厂商后端 493 映射：**0 缺口**【实，脚本 `/tmp/fe_vs_vendor.py`】。
- 厂商前端 232 调用面（bundle 全量）↔ **paas-core** 259 映射：未实现 6 条，扣掉 4 条口径噪声，
  真实剩余 **`/v1/pub/fdfs/upload`、`/v1/pub/file/download`**——这两条由 `gaop-fdfs`（ingress `gaop-api-fdfs`）
  承接，不是 gaop-be 缺口【实，`tools/coverage-audit.py`】。

## 八、复现

```bash
kubectl proxy --port=18001 --address=127.0.0.1        # 后台
python3 tools/probe-vendor-api.py                     # 69/88，退出码 1 表示有失败

# 手动登录（注意本机 HTTP_PROXY 会拦 localhost，curl 必须 --noproxy '*'）
curl -s --noproxy '*' -X POST \
  "http://127.0.0.1:18001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy/v1/pub/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","role":"admin"}'
```

## 九、修复记录：Prometheus（2026-11-09 22:20–22:50）

### 9.1 第一刀：探针超时 1s → 5s（必要，但不是全部）

```
kubectl -n prometheus patch deploy prometheus --type=strategic -p \
 '{"spec":{"template":{"spec":{"containers":[{"name":"prometheus",
   "livenessProbe":{"timeoutSeconds":5},"readinessProbe":{"timeoutSeconds":5}}]}}}}'
```

滚动更新后 `/-/healthy` 从"时通时断"变成稳定 5~7ms【实】，`nodeUsage` 立即恢复：

```json
{"code":0,"data":{"cpuUsage":[{"name":"k8s-master","data":23.72,"alert":false}],
 "memoryUsage":[{"name":"k8s-master","data":82.40,"alert":true}],
 "diskUsage":[{"name":"k8s-master","data":66.70,"alert":false}]}}
```

### 9.2 第二刀：真正的主因是 OOMKilled，不是探针

修完探针后新 Pod 仍在 8 分钟后被杀，`lastState.terminated` 显示 **`reason: OOMKilled`**【实】。
`limits.memory=1Gi`，而它已涨到 **854Mi**，且 head series 有 **71 210** 条（cadvisor 全节点容器指标）。
即：探针超时只是"表象之一"，**内存把 1Gi 打爆才是反复重启的主因**；两者叠加才导致 219 次重启。

处置（不动 limit 值，先压内存）：

```
--storage.tsdb.retention.time=6h  →  2h        # 减少 chunk
env GOMEMLIMIT=850MiB                           # Go 堆软上限，逼 GC 提前回收
```

结果【实】：滚动更新后 **4.5 分钟内 0 重启**，RSS 稳定 862Mi（留 ~160Mi 余量），
`/-/healthy` 200、`cadvisor/node-exporter/kube-state-metrics` 全部 up。

> 若后续仍 OOM：节点共 8GB、已用 6GB（76%），还能给到 1.5Gi；
> 但优先考虑缩小采集面（`kubernetes-apiservers`、`etcd` 两个 target 始终 `unknown`，可先摘掉）。

### 9.3 修复后的接口变化

| 接口 | 修复前 | 修复后 | 说明 |
|---|---|---|---|
| `GET /v1/pub/nodeUsage` | 500 | **200（有数据）** | 主因已除 |
| `GET /v1/pub/nodeMonitor` | 400 | **200（有数据）** | 修复后才发现原先那条 400 是**探针把 `name` 传成了服务名**（`readNode("gaop-be")` 必然 404）；传 `k8s-master` 才公平 |
| `GET /api/v2/dashboards/etcd` | 500/挂起 | **200（有曲线）** | — |
| `GET /api/v2/dashboards/apiServer` | 500/挂起 | **200** | 偶发 6s，可接受 |
| `GET /api/v2/dashboards/cockpit` | 500 | 仍 500 | 与 Prometheus 无关，是 §3.4 的 `limits.cpu` 缺失 |

**总通过率：69/88（78%）→ 71/88（81%）。**

### 9.4 仍未做（需决策）

给 10 个 Deployment 补 `limits.cpu` 会让 cockpit 恢复，但这要**全部滚动重启**厂商控制台，
影响面比 Prometheus 大得多，本轮未动。若要做，建议在维护窗口执行，且 cpu limit 设为节点核数（2）避免误杀。

### 9.5 最终配额（第三刀：只给上限，不动堆软限）

压到 862Mi 后仍只离 1Gi 差 160Mi，且 RSS 还在缓慢上爬，于是补最后一刀：

```
limits:  memory 1Gi → 1536Mi,  cpu 1      # 节点 8GB、当时已用 6.0GB，加 500Mi 后约 80%
env:     GOMEMLIMIT=850MiB                # 堆仍有软上限，不会真涨到 limit
```

复核【实】：Pod 2m51s / **0 重启**，RSS **861Mi**（占新 limit 的 56%），6/6 target up，
关键接口响应从"2~6 秒或超时"降到 **0.09~0.13 秒**：

| 接口 | 状态 | 耗时 |
|---|---|---|
| `GET /v1/pub/nodeUsage` | 200 OK | 0.10s |
| `GET /v1/pub/nodeMonitor?name=k8s-master` | 200 OK | 0.09s |
| `GET /api/v2/dashboards/etcd` | 200 OK | 0.13s |
| `GET /cloud/summary` | 200 | 0.09s |

> 三层防护的分工要分清：**探针 timeout 管"别误杀"**、**GOMEMLIMIT 管"别涨"**、
> **limits.memory 管"涨了也不死"**。只做其中一层都会复发。

## 十、补 `limits.cpu`（2026-11-09 22:55–23:15）

### 10.1 动作

`gaop-namespace` 实际是 **11 个** Deployment（此前说 10 个漏了 `paas-web-preview`）。
逐个 `kubectl patch --type=strategic`，**只加 `limits.cpu: 2`**，`requests` 与 `limits.memory` 原样保留
（strategic merge 对 `limits` 是 map 合并，不会删掉 memory）：

```
kubectl -n gaop-namespace patch deploy <name> --type=strategic -p \
 '{"spec":{"template":{"spec":{"containers":[{"name":"<c>","resources":{"limits":{"cpu":"2"}}}]}}}}'
```

顺序按影响面从小到大：`paas-web-preview` → `paas-web` → `gaop-web` → `gaop-diagnostic` →
`gaop-redis` → `gaop-fdfs` → `gaop-image-uploader` → `gaop-startup` → `gaop-auth` →
`gaop-gateway-auth` → `gaop-be`。每个都等 `rollout status` 到 `1/1` 再进行下一个，避免同时重启抢资源。

取值依据：节点 **4 核**（`allocatable.cpu=4`），当前用量约 1.4 核；给 2 核上限不会 throttle，
也不会增加调度占用（`limits` 不参与调度，只有 `requests` 参与）。

结果【实】：**11/11 全部 `limits={cpu:2, memory:原值}`，`requests` 未变，逐个 `1/1` Ready。**

### 10.2 效果：NPE 确实没了，但 cockpit 换了个地方卡住

补完后 `/api/v2/dashboards/cockpit` **不再是 500 NPE**，而是**挂起 40s 无响应**。
`jcmd 1 Thread.print` 抓到当前线程栈（关键帧）：

```
java.net.PlainSocketImpl.socketConnect(Native Method)          ← 卡在建连
  ...
  feign.Client$Default.execute
  $Proxy183.getCatalogListData
  DockerRegistryApiService.getImageListByProject:36
  K3sImageServiceImpl.getRepositories:274
  K3sImageServiceImpl.getHarborInfo:636
  DashboardsService.cockpit:723                                 ← 已越过 line 548 的 NPE
  DashboardsController.cockpit:88
```

**这恰好证明 §3.4 的 `limits.cpu` 归因是对的**：执行已经推进到 723 行（远在 548 行之后），
NPE 消失；现在卡在 **Harbor（Docker Registry API）不可达**——feign 的 connect 没有短超时，
所以表现为长时间挂起而不是快速报错。

### 10.3 因此本轮通过率不变（71/88）

`cockpit` 仍算失败，但**失败性质变了**：从"厂商代码 NPE（由我们清单缺 cpu 触发）"
变成"外部前提缺失（Harbor 未部署）"。等 Harbor 就位后这条才有机会转 200。

> 副作用提醒：`cockpit` 挂起会长时间占用 Tomcat 线程（池 10），
> 反复刷新驾驶舱页会拖慢其它接口。若近期不部署 Harbor，建议前端先禁用该页的自动刷新。
