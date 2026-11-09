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

88 个 GET 实测：**69 通过（78%），19 失败**。第一轮裸打是 68/20，多出的那 1 条是
`/audit` —— 它的 `start/end` 是 `Long` 毫秒（与 dashboards 的 `Date` 同名字段不同类型），
改传毫秒后 200，属探针参数错误而非接口故障，已从失败数里扣掉。

> 复跑：`python3 tools/probe-vendor-api.py`（需先 `kubectl proxy --port=18001`；详见 §八）。

## 二、失败清单（19 条，全部实测）

| # | 接口 | 现象【实】 | 根因 | 类别 |
|---|---|---|---|---|
| 1 | `GET /v1/pub/nodeUsage` | 500「获取内存状态失败」 | Prometheus 不可用（`SYSTEM_MONITORING=172.16.209.25:30003` 间歇挂） | 组件 |
| 2 | `GET /v1/pub/nodeMonitor` | 400 `k8s.get.cluster.resource.info.failed` | 同上 | 组件 |
| 3 | `GET /api/v2/dashboards/cockpit` | 500 NPE `DashboardsService.cockpit:548` | Prometheus 无 pod 指标 ⇒ `pm.getMetrics()` 为 null，厂商未做空判断（源码 517 行 `pm.getMetrics().isEmpty()`） | 厂商 NPE |
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

- `DashboardsService.cockpit`【码】：`if (!pm.getMetrics().isEmpty())` 未判空。Prometheus 返回空/异常时
  `metrics=null` ⇒ 500。**这与 3.1 联动：Prometheus 一挂，驾驶舱必崩。**
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
| P0 | 把 Prometheus 探针 `timeoutSeconds` 1 → 5（或删 liveness） | 第 1、2、3 条，以及所有监控页的间歇失败 |
| P1 | 给 `gaop-be` 显式注入 `MYSQL_HOST=<本集群 mysql>`、`VIP_HOST`、`HARBOR_HOST`，或确认这些下游"本就不部署" | 第 5~11 条 |
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
