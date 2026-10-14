# `POST /v1/software/overView` 规格 + 实现（第 136 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:753-757` + `SoftwareService.getOverViewInfo:1631-1720` +
`getSoftwareStatus:2073-2089` 逐行读）+〔运行实测〕（真集群 + 真库 + **独立核算**）。2026-10-14。

## 1. 端点

```java
@PostMapping("/overView")
public Response<OverviewInfo> getOverViewInfo()      // 无入参
```
`OverviewInfo{software, service, pod}`，每组是 `StatusInfo{running=0, stop=0, pending=0}`。

## 2. 三组计数——**口径完全不同**（这是本端点最要注意的地方）

### 2.1 `software`

```
for 每条 g_software：
    apps = 该软件的 g_software_app.app_name
    serviceMap = { deployment.metadata.name → 服务 }   ← tenxApi.allPageService(null)，同一次调用
    list = apps 逐个映射：找到 ⇒ 用其 status 推状态串；**找不到 ⇒ "STOP"**
    softwareStatus = getSoftwareStatus(list)：
        空列表 ⇒ "STOP"；RUNNING 计数 == 0 ⇒ "STOP"；== 总数 ⇒ "RUNNING"；否则 "PART_RUN"
    汇总：STOP ⇒ stop++；RUNNING ⇒ running++；**其它（含 PART_RUN）⇒ pending++**
```
⇒ "一个在跑 + 一个关联已不存在" ⇒ `PART_RUN` ⇒ 记进 **pending**。

### 2.2 `service`

```
for 每个 k8s 服务（同上 allPageService）：
    labels 为 null ⇒ 跳过
    appType = labels["system/appType"]
    appType 非 null 且 startsWith("gaop") ⇒ 跳过
    appType 为 null 时：labels["system/appName"] ∈ {gis-server, gis-jb, gaop} ⇒ 跳过
    其余：status 推串，STOP/RUNNING/其它 ⇒ stop/running/pending
```

### 2.3 `pod`

```
列命名空间全部 pod：
    labels["system/appName"] == "gaop" ⇒ 跳过
    phase ∈ {Pending, Terminating} ⇒ pending++；Running ⇒ running++；其它 ⇒ stop++
列 pod 失败 ⇒ **只记日志**（pod 组保持全 0，不影响另外两组）
```

## 3. 实现与验证

**实现**：`IServiceQueryService.querySoftwareOverview(namespace)` + 控制器端点 + `OverviewInfoDTO`；
新增端口 `listAllPods`、DAO/仓储 `selectAllSoftware`；**复用**已有的 `queryServices`（与厂商 `allPageService` 同源，
`GET /v1/service` 已对拉过）、`queryAppNamesBySoftwareId`、以及 `batch-start` 那套 `serviceStatusOf`（Deployment 重载）。

### 3.1 离线：`SoftwareOverviewTest` 5 例，全量 **429** 条 0 失败

覆盖：software 的"一个 RUNNING + 一个找不到 ⇒ pending"、关联为空的软件 ⇒ stop；
service 的四种过滤（`appType=gaop*` / `gis-server` / `gis-jb` / `gaop`）与两种计数；
pod 的 phase 映射与 `system/appName=gaop` 过滤；**列 pod 失败只清零 pod 组**。

### 3.2 在线：真集群 + 真库

**① 基线**（seed 一条 `g_software{sw-ov}` + 两条 `g_software_app{gaop-auth, ghost-ov}`）：

```json
{"software":{"running":0,"stop":0,"pending":1},
 "service":{"running":5,"stop":4,"pending":0},
 "pod":{"running":5,"stop":1,"pending":0}}
```

- `software.pending=1` 正是"gaop-auth 在跑 + ghost-ov 找不到 ⇒ PART_RUN ⇒ pending"（第 2.1 节规则）✓
- `service` 的 `running 5 / stop 4` 与 memory 里 2026-10-14 早先记录的一次实测**完全一致** ✓
- `pod` 组与**独立核算**（直接 `kubectl get pods` 按 phase 计数、排除 `system/appName=gaop`）**逐项相等** ✓

**② 过滤验证**（在 `gaop-namespace` 建三个探针 Deployment，验完删）：
`system/appType`/`system/appName` 必须写在 **Deployment 自身 `metadata.labels`**（厂商读的是
`getDeployment().getMetadata().getLabels()`，**不是 pod 模板的 labels**——第一版探针写错了地方，计数就 +1 了）：

| 探针 | Deployment 自身 labels | 实测 service 计数 |
| --- | --- | --- |
| `ovtest-gaop` | `system/appType=gaop-ovtest` | **不变** ⇒ 被 `startsWith("gaop")` 过滤 ✓ |
| `ovtest-gis` | `system/appName=gis-server`（无 appType） | `stop` **不变** ⇒ 被特殊 appName 过滤 ✓ |
| `ovtest-plain` | 只有 `app=…` | `stop` **+1** ⇒ 未被过滤、按其状态（空 ⇒ STOP）计入 ✓ |

（期间 `running` 有 5→4→3 的漂移，是控制面抖动导致**真实服务状态**变化，与探针无关；关键信号是 `stop` 的三个取值。）
清理后复测回到基线的 `{running:5, stop:4}`，`grep ovtest` 残留 0。

### 3.3 收尾

探针 `g_software`/`g_software_app` 行已删（`g_software`=0、`g_software_app`=0）、三个探针 Deployment 已删、
临时 NodePort 与后端进程已回收；实验室其余数据未动。
