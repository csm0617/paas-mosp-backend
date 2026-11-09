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

## 十一、`GET /v1/service/gaop` 的 NPE 定位与修复（2026-11-09 23:05–23:25）

用户报障：`/api/gaop/v1/service/gaop?type=2&page=1&size=10&keyword=` 返回
`500 {"msg":"系统错误","code":-1}`，trace 首帧
`NullPointerException at ServiceController.getGaopServiceList(ServiceController.java:1961)`。

### 11.1 定位：字节码 + 环境事实交叉验证

`javap -p -l -c` 查 `ServiceController.class`（来自运行镜像 `gaop-be-0.0.1.jar`），
`line 1961` → 偏移 898，指令序列是：

```
885: aload 15
887: ServicesItem.getDeployment()
890: IoK8sApiExtensionsV1beta1Deployment.getMetadata()
893: ...ObjectMeta.getName()
896: astore 19
898: aload 15                                    ← line 1961 起点
900: getDeployment() → getSpec() → getTemplate()
909: ...PodTemplateSpec.getMetadata()
912: ...ObjectMeta.getAnnotations()              ← 返回 null 时
915: ldc "gaop/serviceLabel"
918: Map.get(...)                                ← 这里 NPE
```

即代码等价于：

```java
servicesItem.getDeployment().getSpec().getTemplate()
            .getMetadata().getAnnotations().get("gaop/serviceLabel")
```

`getAnnotations()` 在 Pod 模板没有注解时返回 **null**（不是空 Map），紧接着 `.get()` 就抛 NPE。

集群侧复核【实】：**4 个 Deployment 的 `spec.template.metadata.annotations` 为 null** ——
`gaop-diagnostic`、`gaop-redis`、`gaop-startup`、`gaop-web`；
其余 7 个因为有 `kubectl.kubernetes.io/restartedAt` 才躲过（说明厂商假设"平台创建的服务一定带注解"，
而我们是手工 apply 的清单）。

### 11.2 修复

给 4 个 Deployment 补注解（顺带补上厂商语义里的展示名）：

```
kubectl -n gaop-namespace patch deploy <name> --type=strategic -p \
 '{"spec":{"template":{"metadata":{"annotations":{"gaop/serviceLabel":"<name>"}}}}}'
```

结果【实】：`type=1` 与 `type=2` **均 200**，`total=6`；走网关 `172.16.209.170/api/gaop` 复验同样 200。
全量通过率 **71 → 72 / 88（82%）**。

### 11.3 两条重要教训

1. **`recovered/` 的反编译源码与运行镜像不是同一版本。** 源码里 `getGaopServiceList` 在 1564 行且是纯查询；
   jar 里同一方法行号是 1806–1966。**定位线上异常必须以 jar 的字节码为准**
   （`unzip` 出 class + `javap -l` 查 LineNumberTable），不能直接按反编译源码的行号对。
2. **`getAnnotations()` / `getLabels()` 在 Java k8s client 里会返回 null**，不是空集合。
   厂商大量代码直接链式 `.get(...)`，因此"手工 apply 的工作负载"很容易踩到这类 NPE。
   我们自己写清单时要保证 `template.metadata.annotations` 至少有一个 key。

> 同类隐患：`ServiceController:1686` 还有一处 `getAnnotations().put(...)` 同样没判空，
> 只是 `/v1/service` 恰好被前面的过滤条件跳过了。补注解顺带把这条也压住了。

## 十二、服务详情报「服务不存在」：缺 `system/svcName` 标签（2026-11-09 23:15–23:45）

用户报障：`GET /api/gaop/v1/service/gaop-diagnostic` 返回
`400 {"msg":"服务gaop-diagnostic不存在","code":400}`，trace 指
`ServiceController.getServiceDetail:1611`。

### 12.1 根因：不是 TenX 平台，是 K8s 标签选择器

`javap` 看 `getServiceDetail` 前 70 个字节码偏移，逻辑只有三步：

```
0:  tenxCloud.clusters().getClusters().get(0).getClusterID()
25: tenxCloud.existService(clusterID, serviceName)
37: existService.isData() → ifne 67
48: new WrongArgumentException("service.service.name.not.found", serviceName)  ← line 1611
```

而 `TenxService.existService` 的实现是**直接查 K8s**，不碰 TenX 服务：

```java
appsV1Api.listNamespacedDeployment(NAMESPACE, ..., "system/svcName=" + deployName, ...);
if (size == 0)
    appsV1Api.listNamespacedStatefulSet(NAMESPACE, ..., "system/svcName=" + deployName, ...);
```

**我们用这个 selector 实测：`kubectl get deploy -n gaop-namespace -l system/svcName=gaop-be` → 空。**
即所有服务都"不存在"，与具体是哪个服务无关（`gaop-be`、`gaop-web` 同样 400，只有 `nginx-not-exist` 才该 400）【实】。

### 12.2 厂商创建服务时会写 5 个标签 + 1 个注解

`PublicController.createService`（源码 822–835）【码】：

```java
deployment.metadata.labels.put("app",            serviceName);
deployment.metadata.labels.put("name",           serviceName);
deployment.metadata.labels.put("system/appName", serviceName);
deployment.metadata.labels.put("system/svcName", serviceName);   // ← 详情接口靠它找服务
deployment.metadata.labels.put("system/appType", "business");
deployment.spec.template.metadata.labels         // 同一套（app/name/appName/svcName/appType）
deployment.spec.template.metadata.annotations.put("gaop/serviceLabel", serviceName);
```

类型枚举 `AppTypeLabelsEnum`：`gaop-base`（基础服务）/ `gaop-core`（框架服务）/ `business`（业务）。

**我们的清单只有 `app` 和 `system/appName: gaop`**，缺 `name` / `system/svcName` / `system/appType` / `gaop/serviceLabel`
—— 这就同时解释了本文第十一节（annotations 为 null → NPE）和本节（svcName 缺失 → 不存在）**两个报障**。

### 12.3 修复：只补 `metadata.labels.system/svcName`（不重启 Pod）

```
kubectl -n gaop-namespace patch deploy <name> --type=merge -p \
 '{"metadata":{"labels":{"system/svcName":"<name>"}}}'
```

改 `metadata` 不会触发滚动更新（只有 `spec.template` 变了才会）。9 个 gaop-* 全部补齐，
`paas-web` / `paas-web-preview` **故意不加**（它们不是厂商服务，加进去会污染服务列表）。

结果【实】：

| 接口 | 修复前 | 修复后 |
|---|---|---|
| `GET /v1/service/gaop-diagnostic` | 400 不存在 | **200**（0.83s，返回 service+deployment 详情） |
| 其余 8 个 gaop 服务详情 | 全部 400 | **全部 200**（0.10–0.43s） |
| `GET /v1/service/gaop?type=2` | 200，total=6 | 200，total=6（**未受影响**） |

### 12.4 刻意没做：`system/appType`

加 `system/appType` 会让 `getGaopServiceList` 的类型过滤真正生效
（type=1 只留 `gaop-core`，type=2 只留 `gaop-base`），而现在 appType 为 null 时走名字匹配兜底，
两个 tab 都能看到 6 个服务。**在没确认 9 个内置服务各自该归 core 还是 base 之前，贸然打标会让列表变空**。
这一项留作待办。

### 12.5 固化

运行时 patch 在集群重建后会丢，且这批清单是**手工 apply** 的（集群里没有 helm release，厂商 chart 也不写这些标签）。
已提供幂等脚本：`tools/align-vendor-service-labels.sh [--dry-run] [--with-label]`
—— 默认只补 svcName（不重启）；`--with-label` 才补 `gaop/serviceLabel`（会滚动更新）。

> 给重写版的提醒：`paas-core` 实现服务详情时**不要**依赖 `system/svcName` 标签
> （那是厂商的历史约定），直接用 Deployment 名查即可；若为兼容老数据需要回退，也应先按名查、标签仅作兜底。

## 十三、点「配置」整页白屏：env 里的 `secretKeyRef` 打崩前端表单（2026-11-09 23:30–00:00）

用户报障：基础服务列表点操作列「配置」，整页变白。

### 13.1 复现与定位（浏览器实测，不是猜）

用 playwright 登录厂商前端 → `/gaop/software/application/base-service` → 点第一行「配置」，
抓到【实】：

```
[pageerror] TypeError: Cannot read properties of null (reading 'fieldPath')
    at /gaop/5328.f3271109.async.js:1:83149
    at Array.map (<anonymous>)
```

**同时 4xx/5xx 请求为 0** —— 所以不是接口问题，是纯前端渲染崩溃（React 树被错误边界清空 ⇒ 白屏）。

在该 chunk 的 83149 处是 `ServiceConfig` 的 env 归一化函数：

```js
A = function (e) {
  var t = e.map(function (e) {
    return { ...e,
      value: e.value ? e.value
           : (e.valueFrom ? re[e.valueFrom.fieldRef.fieldPath] : e.value),
      type: e.value ? "text" : "podType", isOldEnv: !0 };
  });
```

它的隐含假设是 **env 只有两种形态**：`value`（普通值）或 `valueFrom.fieldRef`（引用 Pod 字段）。
一旦 `valueFrom` 是 `secretKeyRef`，`fieldRef` 就是 undefined ⇒ 取 `.fieldPath` 直接抛。

### 13.2 为什么我们会踩到：6 个服务、14 个 env 用了 `secretKeyRef`

```
gaop-auth / gaop-be / gaop-fdfs / gaop-diagnostic / gaop-gateway-auth / gaop-image-uploader
  GAOP_MYSQL_USERNAME  <- secretKeyRef gaop-app/username
  GAOP_MYSQL_PASSWORD  <- secretKeyRef gaop-app/password
  （gaop-fdfs 另有两个 <- gaop-minio/rootUser、rootPassword）
```

厂商平台自己创建服务时只写 `value` 或 `fieldRef`，所以这个前端分支在他们自己的环境里从没被触发过；
我们的清单为了"密码不进 spec"用了 `secretKeyRef`，正好撞上。

### 13.3 修复：换成字面 `value`

把这 14 项替换为 `{name, value}`（值取自对应 Secret），用 json patch 整体替换 env 数组，逐个滚动：

```
kubectl -n gaop-namespace patch deploy <name> --type=json \
  -p '[{"op":"replace","path":"/spec/template/spec/containers/0/env","value":[...]}]'
```

结果【实】：点「配置」不再白屏，抽屉正常打开
（标题「服务配置：gaop-auth」，左侧锚点 基本信息/环境变量/资源配置/…，
环境变量表把 6 个变量全部识别为「普通变量」，`GAOP_MYSQL_USERNAME=gaop`、`GAOP_MYSQL_PASSWORD=gaop123456` 正确）。

**代价**：密码明文进了 Deployment spec（有 `get deploy` 权限即可读）。
这是**厂商前端能力缺陷**逼出来的取舍，仅适用于测试环境。
生产要么修前端（支持 secretKeyRef 回显），要么改用厂商的配置项机制（`g_setting`），不要把明文留在 spec。

### 13.4 检测工具

```
tools/check-vendor-env-shape.sh [namespace]     # 只读，发现即退出码 1
```

接进巡检即可避免"改完清单又白屏"。

### 13.5 顺带核查：`ws://localhost:9999` **不是缺陷，不改**

白屏复现时 console 里还有一条 `WebSocket connection to 'ws://localhost:9999/' failed`。
查产物后确认它的性质：

```js
var L = "/api/gaop";
var N = new WebSocket("ws://localhost:9999");            // ① 本地客户端通道（硬编码）
N.onmessage = e => { if ("auto-login-client" === t.key) document.cookie = t.payload } // 自动写登录 cookie
...
I.webSocketFactory = () => new SockJS(L + "/ws");        // ② 实时推送（STOMP，相对路径）
I.activate();
```

- **① `ws://localhost:9999` 是厂商桌面客户端的约定端口**：客户端在本机起 WS 服务，浏览器端连上后接收
  `auto-login-client` 消息自动注入 `gateway-token`。在浏览器里访问本就没有这个客户端，
  连不上是**设计内的预期**，而且代码只 `new` 一次、没有重连、`isClient` 也不会被误置 true。
- **② 真正的实时推送是 STOMP over SockJS，走相对路径 `/api/gaop/ws`**，与访问地址同源，配置正确。

浏览器实测【实】：

```
WS OPEN ws://172.16.209.170/api/gaop/ws/834/eqgk5bam/websocket
GET  /api/gaop/ws/info?t=...
log: socket 连接成功
```

**结论：不改。** 收益为零（只是少一条 console 噪音），代价却是改厂商打包产物（升级即被覆盖）
并可能破坏桌面客户端集成。若确实要消除噪音，正确做法是让客户端侧或产品侧决定，而不是在服务端产物里打补丁。

> 给重写版的提醒：`paas-web` 的服务配置页必须同时兼容
> `value` / `fieldRef` / `secretKeyRef` / `configMapKeyRef` / `resourceFieldRef` 五种形态，
> 不能像厂商那样只认前两种；render 前对 `valueFrom` 做完整判空。

## 十四、`GET /v1/pub/vip` 500：补一个 arm64 的 VIP 守护进程（2026-11-09 23:55–00:15）

用户报障：`/api/gaop/v1/pub/vip` 返回
`500 feign.RetryableException: connect timed out executing GET http://192.168.1.52:5556/api/vips`。

### 14.1 根因是"组件不可用"而不是"配置写错"

- 地址来源：`vip.host = ${VIP_HOST:${SSH_HOST:localhost}}`，`vip.port=5556`；
  Pod 里 `SSH_HOST=192.168.1.52`（老环境），`VIP_HOST` 未设 ⇒ 打到老地址【实】。
- 更根本的是：**厂商只提供 amd64 的宿主二进制**
  `msop/amd64/vip/vip-daemon`（`file` 显示 x86-64、Go 静态、`-s -w`），
  而节点是 arm64 ⇒ 就算把 `SSH_HOST` 改对也没有 daemon 在 5556 上监听。
- 所以单纯改配置无效，必须先有一个能听的进程。

### 14.2 没有走反编译，直接按契约重写

`vip-daemon` 是 `-s -w` 编的 Go 程序：`go tool nm` 报 `no symbol section`，
但 pclntab 仍可读出 **1517 个函数名**（`main.NewVIPManager`/`NewNetworkManager`/
`isHigherNode`/`detectCurEtcd`/`checkLinkHealth` 等），字符串里能拿到全部路由
（`/api/vips`、`/api/vips/add|delete`、`/api/interfaces`、`/api/config/sync`、`/api/configmap`）。
**但把汇编还原成可维护代码的成本远高于重写**，且接口契约本来就完整：

- `VipDaemonApi.java`：7 个方法（GET/POST `/api/vips`、`/add`、`/delete`、`/api/interfaces`）
- `VipItem`：`{interface, vip}`（vip 形如 `10.0.0.5/32`）
- `InterfaceInfo`：`{name, mac, flags, addresses[]}`（`index` 是 `@JsonIgnore`）
- `config.json.j2`：单节点分支 `vips: []`

### 14.3 实现与部署

代码在 `deploy/cluster/vip-daemon/`（单文件 Go、纯标准库、`CGO_ENABLED=0` 交叉编译成 arm64）：

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/vips` | `[]`（单节点无 HA，与厂商模板一致，是真实状态） |
| GET | `/api/interfaces` | 标准库 `net.Interfaces()` 读**宿主机**真实网卡 |
| POST | `/api/vips`、`/add`、`/delete` | **501 + 原因**（不假装成功） |
| GET | `/health` | 探针 |

部署要点：`DaemonSet` + `hostNetwork: true`（容器自己的 netns 只有 eth0+lo，看不到宿主网卡），
`ConfigMap` 提供单节点配置，`Service` 暴露 5556，`limits` 里 cpu/memory **成对写**
（§3.4 的教训：厂商 cockpit 假设 limits 成对）。

镜像走离线导入（`docker import` 不用拉基础镜像 → 5.3MB tar）：
临时 Pod 挂 `/run/docker.sock` + **`runAsUser: 0`**（非 root 读不了 660 的 socket，
curl 会报 `couldn't connect` exit 7），`curl --unix-socket` 打 `/images/load`，用完即删。

### 14.4 结果【实】

```
GET http://172.16.209.25:5556/api/vips
→ []
GET http://172.16.209.25:5556/api/interfaces
→ [{"name":"ens160","mac":"00:0c:29:00:ff:2e","flags":"up|broadcast|multicast|running",
    "addresses":["172.16.209.25/24","fe80::20c:29ff:fe00:ff2e/64"]}]

gaop-be: VIP_HOST=vip-daemon（Service 10.96.1.210）
GET /v1/pub/vip            → 200 {"code":0,"data":[]}        （原 500）
GET /v1/pub/vip/interfaces → 200 真实网卡
```

浏览器验证 `/gaop/netmanager/vipmanage`：表头（序号/虚拟IP/绑定网卡/操作）+「新增虚拟IP」
正常渲染，**0 个 4xx/5xx、0 个 pageerror**，表格显示「暂无数据」——这是单节点的正确状态。

### 14.5 与既有文档的差异

findings/100 把这条列为"VIP daemon 未部署，不在安装包内"。**实际在包里**
（`amd64/vip/vip-daemon` + `templates/vip.service.j2` + `config.json.j2`），
只是架构是 amd64，且安装方式是宿主机 systemd 而不是容器。

### 14.6 补充：拿厂商二进制做对拉，改掉 5 处不一致（2026-11-10 00:15）

初版实现是"照契约猜"的，随后发现**厂商的 amd64 二进制其实能跑**——Docker Desktop 的 amd64 模拟足够，
于是把 `amd64/vip/vip-daemon` 打成镜像在本地起了一份（`v1.0.9-20260731-link-health`），
用同一份 config、同一批请求对拉。**第一次对拉就抓到 5 处偏差**：

| 项 | 厂商【实】 | 我们初版 | 处置 |
|---|---|---|---|
| `InterfaceInfo.index` | 输出 `{"index":1,...}` | 省略 | 补；Java 的 `@JsonIgnore` 只作用于 gaop-be→前端，不影响 daemon→gaop-be 反序列化 |
| 网卡过滤 | **不过滤**（`lo`/`cali*` 原样返回） | 按前缀过滤 | 去掉；过滤在 `PubVipController.interfaces()` |
| 空 `addresses` | `null` | `[]` | 改 nil 切片 |
| 无标志 `flags` | `"0"` | `""` | 映射 |
| `/api/config/sync`、`/api/configmap` | **404**（strings 残留，未注册） | 501 | 删路由，保持 404 |

**保留的两处有意偏离**（记 B 类）：写接口厂商是 `200 + 文本 OK`（先写内存并回写 `config.json`，
再异步 `ip addr`/`nmcli`，失败也 200），我们返回 501；`/health` 厂商 404，我们 200 给探针用。

**连带发现**：daemon 改成原样返回后，`/v1/pub/vip/interfaces` 冒出 **38 个网卡**——
`application.yml` 的 `vip.ignore-prefix-list` 默认值没有 `cali`/`tunl`。
给 gaop-be 补 `VIP_IGNORE_PREFIX_LIST=lo,docker,kube-,flannel,usb,virbr,cni,veth,cali,tunl`
（Spring 的 List 支持逗号分隔绑定）后只剩真实网卡 `ens160`。

**方法结论**：这个组件能对拉就不该猜。判据是 `VipDaemonApi` 的 7 个方法与两个 DTO，
对拉一次比读 1500 个函数名有用得多。

## 十五、基础服务列表的 `--`：服务名与镜像是两套来源（2026-11-10 01:00–01:30）

用户截图：`/gaop/software/application/base-service`（菜单「框架服务 / 系统镜像」）里，
3 行「服务名称」是 `--`，3 行「镜像」是 `--`。

### 15.1 两个列的数据源（前端硬编码路径，【码】来自 `p__BaseServices` chunk）

```js
// 服务名称
dataIndex: ["deployment","spec","template","metadata","annotations","gaop/serviceLabel"],
render: e => <Tooltip title={e}>{e || "--"}</Tooltip>

// 镜像：取容器 image 后按 "system_containers/" 前缀切分
```

所以：

- **服务名称** = Pod 模板注解 `gaop/serviceLabel`，没有就 `--`。实测 9 个 gaop 服务里
  只有 5 个有这个 key（`auth/be/gateway-auth` 与 `fdfs/image-uploader` 没有）。
  这与第十一节是**同一批注解**，只是上次只为"修 NPE"补了 4 个，没做全量。
- **镜像** = 容器 `image` 字符串，**只有带 `system_containers/` 前缀的才能解析出**
  `name:tag`；`gaop-be:V1.3.4-...` 这种无前缀的直接显示 `--`。

### 15.2 处理

| 问题 | 处置 |
|---|---|
| 9 个服务缺 `gaop/serviceLabel` | 全部补上（`auth/be/gateway-auth/fdfs/image-uploader` 5 个） |
| `gaop-be` 镜像缺前缀 | 厂商原版 RepoTags 是 `system_containers/gaop-be:V1.3.5-...`，我们导入 arm64 版时丢了前缀；`docker tag` 补 `system_containers/gaop-be:V1.3.4-...` 并更新 Deployment |
| `gateway-auth` 镜像缺前缀 | 厂商原版就是 `gaop-gateway-auth:v1.0.12`（无前缀），页面 `--` 是**厂商前端缺陷**；为让显示完整也补了前缀 |
| `redis` 镜像缺前缀 | 厂商原版 `redis:7.4` 同样无前缀 ⇒ 保持原样，该列仍显示 `--`（与厂商一致） |

### 15.3 踩到一个会自我复制的坑

把 `gaop-redis` 的 image 改成 `system_containers/redis:7.4` 之后，**`gaop-startup` 判定"redis 服务缺失"，
自动创建了一个全新的 `redis` Deployment + Service**（labels 齐全：`system/appName=gaop`、
`system/appType=gaop-base`、`system/svcName=redis`，但 **annotations 为 null**）。

后果：`GET /v1/service/gaop?type=2` 立刻回到 §十一 的 NPE（`getAnnotations()` 为 null）。
也就是说**一次"修显示"的操作引发了一个新的 500**。

处置：把 `gaop-redis` 镜像改回 `redis:7.4`，删掉重复的 `redis` Deployment/Service，
观察 100 秒未再重建，`type=1/2` 均 200、`total=6`。

> 教训：厂商这批"基础服务"有 `gaop-startup` 兜底创建（日志里能看到 `serviceName: gaop-redis`）。
> **改这些服务的 image 名会被判定为"服务缺失"而重建一份**，命名必须与厂商保持一致。
> 想改显示只能改前端，不能改 image。

## 十六、「资源概览」的日志存储两个环永久转圈：Secret `loki/loki` 不存在（2026-11-10 02:10–02:30）

用户报障：`/gaop/ops/status-monitor/resources` 里「日志存储容量」「日志存储时长」两个环一直在转，
标题还显示「存储时长 : 0 天」。

### 16.1 前端判据：只看一个布尔值

`p__StatusMonitorResources` 里两个卡片是同一套写法（【码】）：

```js
useState(!1)                                  // 默认不转
useEffect(() => {
  !n.lokiPodStatus || n.deleteStatus ? c(!0) : c(!1)   // lokiPodStatus=false 或 deleteStatus 就转
}, [n])
render: <Spin spinning={l}>{...ring...}</Spin>
```

即：**只要 `lokiPodStatus` 为 `false`，就无条件永久转圈**，与数据是否为空、请求是否成功无关。
所谓「数据没出来所以转」是误判——它是被这个布尔值锁死的。

### 16.2 接口实际返回：`lokiPodStatus: false`

```
GET /loki/logs/getLogsAssertInfo → 200
{"totalMemory":0.0277,"logMaxMemory":300.0,
 "logRetainDay":null,"logRetainMaxDay":null,
 "logMaxMemoryStatus":"NORMAL","logRetainDayStatus":null,
 "lokiPodStatus":false}
```

注意 `totalMemory` 与 `logMaxMemory` **都有值**，说明方法前半段跑通了；`logRetainDay/logRetainMaxDay`
为 null，说明异常发生在中间。对照 `LokiApiService.getLogsAssertInfo()`（【码】）的赋值顺序：

```java
setLokiPodStatus(true);                       // 先乐观置 true
setTotalMemory(getLogTotalMemory());          // ✅ du -sh /data/loki/ 成功
setLogMaxMemory(getLokiLogMaxMemory());       // ✅ 300（来自 g_setting）
... status = NORMAL
int retainMaxDay = getLokiLogRetainMaxDay();  // ❌ 抛异常
...
catch (Exception e) { log.error("pod:loki-0运行异常或者正在重启中:", e);
                      setLokiPodStatus(false); }   // ← 这里被改回 false
```

**catch 是兜底全量异常**，所以任何一步失败都会让整个面板转圈，而错误信息只留在日志里。

### 16.3 根因：后端从 Secret 读配置，我们的清单只有 ConfigMap

后端日志给出确切异常【实】：

```
SystemException: k8s.call.api.failed
  at LokiApiService.getLokiYamlFromSecret(LokiApiService.java:398)
  at LokiApiService.getLokiLogRetainMaxDay(LokiApiService.java:234)
  at LokiApiService.getLogsAssertInfo(LokiApiService.java:108)
```

`getLokiYamlFromSecret("loki", "loki")` 的实现（【码】）：

```java
V1Secret secret = coreV1Api.readNamespacedSecret("loki", "loki", null);  // 命名空间 loki、名字 loki
Map<String,byte[]> data = secret.getData();
if (data == null || !data.containsKey("loki.yaml"))
    throw new SystemException("Secret does not contain loki.yaml");
```

也就是硬性要求 **Secret `loki/loki` 且键名必须是 `loki.yaml`**（内容为完整 Loki YAML，
且含 `limits_config.retention_period`，后端据此算保留天数：`168h → 168/24 = 7 天`）。

而集群实际情况【实】：

```
kubectl get secret -n loki          → No resources found
kubectl get sts loki -n loki        → volumes: configMap loki-config（键 config.yml）
```

**没有 Secret，键名也不对** ⇒ `readNamespacedSecret` 404 ⇒ `k8s.call.api.failed` ⇒ 转圈。
这与第十一/十二节同源：厂商假设对象由平台创建（含 Secret），我们手工 apply 的清单少了这层隐性契约。

### 16.4 修复：把配置源改成这个 Secret（而不是造一个假 Secret）

只建 Secret 不改挂载，能让接口返回 200、环不转，但**设置面板会变成假入口**——后端改的是
Secret，Loki 读的仍是 ConfigMap，改了保留天数并不生效。所以按"单一配置源"处理：

1. 用现有 ConfigMap 内容创建 Secret（键 `loki.yaml`）
2. 把 StatefulSet 的 `config` 卷从 ConfigMap 切到该 Secret，
   并用 `items` 保持容器内文件名仍为 `config.yml`（**不动 `-config.file` 启动参数**）

```
kubectl create secret generic loki -n loki --from-file=loki.yaml=/tmp/loki.yaml
kubectl patch sts loki -n loki --type=strategic -p '{"spec":{"template":{"spec":{"volumes":[
  {"name":"config","configMap":null,"secret":{"secretName":"loki","items":[
    {"key":"loki.yaml","path":"config.yml"}]}}]}}}}'
```

结果【实】：

| 项 | 修复前 | 修复后 |
|---|---|---|
| `lokiPodStatus` | `false` | **`true`** |
| `logRetainMaxDay` | `null` | **`7`** |
| `logRetainDay` / 状态 | `null` | `0` / `NORMAL` |
| 接口耗时 | — | 1.4s |
| 页面转圈块数（`.ant-spin-spinning`） | 2 | **0** |
| 卡片标题 | 「存储时长 : 0 天」 | **「存储时长 : 7 天」** |
| 失败请求 / pageerror | — | **0 / 0** |

> `totalMemory` 从 0.0277 降到 0.0016 是 Loki 重建后 `emptyDir` 被清空所致（**数据不持久**，
> 这是清单用 emptyDir 的既有问题，与本次修复无关，见 16.6）。

### 16.5 写路径现在是真生效（但仍有一处风险）

`PUT /loki/logs/setLokiLogRetainMaxDay?val=N` 的实现是：读 Secret 里的 YAML →
用 SnakeYAML 改 `limits_config.retention_period` → `replaceNamespacedSecret` → **删除 loki-0 触发重启**。
配置源统一后这条链路是真生效的，不再是假入口。

但**SnakeYAML 是整棵树 load→dump**，回写后 YAML 会被重排/改风格（注释、缩进、
`from: 2020-10-24` 这类日期标量的序列化形态都可能改变）。所以第一次用该面板改保留天数之前，
建议先备份：`kubectl get secret loki -n loki -o yaml > /tmp/loki-secret-backup.yaml`。
本条**未做实机写入验证**（会重启 Loki 且可能改写配置），只做了只读验证。

### 16.6 顺带记录两个未处理的既有问题

- Loki 的 `/data` 是 **emptyDir(5Gi)**：Pod 重建即丢日志，且「日志存储容量」永远接近 0。
  要长期可用需换 PVC。本次未动（属容量规划，不在报障范围内）。
- 老的 ConfigMap `loki-config` 从此**不再是生效配置**，改它不会有效果。
  保留它只作备份；更稳妥的做法是删掉或加注释说明，避免后来人改错对象。

### 16.7 固化

运行时改动在集群重建后会丢，已提供幂等脚本：

```
tools/align-loki-config-source.sh [--dry-run] [命名空间]
```

脚本会检查 Secret 是否存在、StatefulSet 是否已指向它，已就绪则全部 SKIP；
若 ConfigMap 里没有 `limits_config` 会直接中止（否则后端解析保留天数仍会失败）。

## 十七、镜像上传不通：上传服务指向厂商旧仓库 + docker 不信任本集群 registry（2026-11-10 02:24–02:40）

用户报障：`/api/gaop/v1/image/repositories?storage=gaop&page=1&size=10&keyword=` 相关页面
「镜像上传接口没通」。

### 17.1 先纠正两个容易跑偏的观察

1. **`/v1/image/repositories` 并没有报错**：实测 200 `{"list":[],"total":0}`。
   空的含义是 registry 的 catalog 里还没有 `gaop/` 前缀的仓库（`getImageListByProject` 是
   `GET /v2/_catalog` + 按 `project + "/"` 过滤）。**没上传过 = 空列表**，不是接口坏。
2. **`/v1/image/upload` 不在 gaop-be 里**。直连 gaop-be 打这个路径得到的是 Spring 的
   `404 {"status":404,"path":"/v1/image/upload"}` —— 我第一轮就被这个 404 误导过。
   它实际由 **`gaop-image-uploader`** 提供（该类类级 `@RequestMapping("/v1/image")` + 方法 `/upload`），
   网关里有专门的 ingress 把它分流过去：

   ```
   gaop-api-image-upload   /api/gaop/v1/image/upload  → gaop-image-uploader:18080  (rewrite → /v1/image/upload)
   gaop-api-image-download /api/gaop/v1/image/download→ gaop-image-uploader:18080
   gaop-api-be             /api/gaop(/|$)(.*)         → gaop-be:8080
   ```

   > 教训：**先走真实入口（网关 172.16.209.170）再判断，直连后端会得到误导性的 404**。

### 17.2 上传是异步的，200 不代表成功

`POST /v1/image/upload?storage=gaop&username=admin&type=1`（multipart，字段名 `file`）
入队即返回 `{"code":0,"data":true}`，真正的工作在后台：`docker load` → 读 ImageMeta →
`docker tag` → `docker push`。结果只能从 **`GET /v1/image/imageLog`** 的 `status`/`log` 看。

用一个真实镜像（`docker save` 出来的 5.3MB tar）实测，暴露两级真实故障：

### 17.3 故障一：上传服务把镜像 push 到厂商旧环境

```
The push refers to repository [192.168.1.171:80/gaop/vip-daemon]
Get "https://192.168.1.171:80/v2/": context deadline exceeded
```

`gaop-image-uploader` 的 `application.yml`【码】：

```yaml
server:
  host:
    harbor: http://${HARBOR_HOST:192.168.1.171:1080}
docker:
  registry-host: ${server.host.harbor}
```

而 Deployment 里**只注入了 `HARBOR_USERNAME` / `HARBOR_PASSWORD`，没有 `HARBOR_HOST`**
⇒ 走镜像默认值 `192.168.1.171:1080`（拼 tag 时端口被丢掉，于是变成 `:80`）⇒ 15 秒连接超时。

处置：给 uploader 补 `HARBOR_HOST=172.16.209.25:30180`（与 gaop-be 已用的一致）。

### 17.4 故障二：docker 用 HTTPS 访问纯 HTTP 的 registry

改完地址后 push 目标正确了，但立刻换成另一个错：

```
The push refers to repository [172.16.209.25:30180/gaop/vip-daemon]
Get "https://172.16.209.25:30180/v2/": http: server gave HTTP response to HTTPS client
```

本集群的 registry 是 sealos 起的 `docker-registry`（NodePort 30180 → 5000），**纯 HTTP**；
docker daemon 默认对未信任的 registry 走 HTTPS。
节点 `/etc/docker/daemon.json` 原来只有 `"insecure-registries": ["sealos.hub:5000"]`。

处置（与厂商 `roles/docker/templates/daemon.json.j2` 的做法一致）：

1. 备份 `/etc/docker/daemon.json`
2. 在 `insecure-registries` 里追加 `172.16.209.25:30180`
3. **给 dockerd 发 SIGHUP 重载**——`insecure-registries` 属于可热重载项，
   **不能 restart docker**（默认 `live-restore=false`，重启会停掉节点上所有容器）

> 落地方式：起一个临时特权 Pod（`hostPID: true` + hostPath `/etc/docker` + `/run/docker.sock`），
> 改文件后 `kill -HUP <dockerd pid>`，验证完即删。改配置前先用本地 python 校验 JSON 合法性，
> 写坏 daemon.json 会导致下次 docker 重启时直接起不来。

### 17.5 结果【实】

| 阶段 | 上传前 | 现在 |
|---|---|---|
| push 目标 | `192.168.1.171:80`（旧环境） | **`172.16.209.25:30180`** |
| 协议 | HTTPS → HTTP 被拒 | 走 insecure 通道，正常 |
| `imageLog` 尾部 | `context deadline exceeded` / `server gave HTTP response to HTTPS client` | `AuxDetail(digest=sha256:1c0ab5…)+ 上传镜像成功` |
| `repositories?storage=gaop` | `total=0` | **`total=1`（`gaop/vip-daemon`）** |
| 删除（`DELETE /v1/image?name=gaop/vip-daemon&tag=arm64-local`） | — | 200，列表回到 0（测试镜像已清理） |

### 17.6 厂商缺陷（A 类，不复制）

02:31 那次 push **明确失败**（`errorDetail: Get "https://…/v2/": server gave HTTP response to HTTPS client`），
但 imageLog 里 `status` 仍是 `1`（成功），末尾还打印「上传镜像成功」。
即 **`DockerService.uploadImage` 没有检查 `ResponseItem.errorDetail`**，UI 会误报成功。
我们的重写版必须逐帧检查 push 事件流里的 `errorDetail`，不能沿用这个判断。

### 17.7 固化

```
tools/align-image-registry.sh [--dry-run] [registry地址]
```

做两件事：上传服务 `HARBOR_HOST` 归位；节点 `daemon.json` 的 `insecure-registries` 补齐并
SIGHUP 重载（带备份、幂等、临时 Pod 用完即删）。

> 仍未处理：`g_setting.system.url.harbor` 为 NULL、`system.url.container_log = 128.0.42.3`（旧地址），
> 以及 `projectList` 返回的 `creation_time` 是 `+58827-06-02` 这类离谱值（厂商时间解析 bug）。
> 都不影响上传主链路，先记录。
