# `PUT /v1/service/healthCheck` 规格（第 126 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.healthCheck:2658-2689` + `patchReadiness:2749-2784` + `patchLiveness:2785-2820` + `HealthSetInfo` 逐行读）。2026-10-14。本轮**读全规格并实现**。

## 1. 端点与入参

```java
@PutMapping("/healthCheck")
public Response<Boolean> healthCheck(@RequestBody HealthCheckForm form)
```

`HealthCheckForm`：`name`（服务名）、`readiness`、`liveness`（都可为 null）。
`HealthSetInfo`：`type`、`path`、`port`、`initialDelaySeconds`、`timeoutSeconds`、`periodSeconds`、
`successThreshold`、`failureThreshold`。

## 2. 流程

```
1. 读 Deployment(form.name)：ApiException ⇒ 400 `service.not.found.update.image.version`（**带服务名**）
2. container = spec.template.spec.containers.get(0)
3. readiness != null ⇒ patchReadiness(container, readiness)
   liveness  != null ⇒ patchLiveness(container, liveness)
4. containers.set(0, container) 后**整份 merge-patch** Deployment
5. 成功 ⇒ 200 `{code:0,data:true}`；patch 的 ApiException ⇒ 400 `k8s.call.api.failed.to.change.image.version`
```

## 3. `type` 的三态（readiness 与 liveness **同一套规则**）

| `type` | 行为 |
| --- | --- |
| `0` | **清空**对应的探针（`setReadinessProbe(null)` / `setLivenessProbe(null)`） |
| `1` | **HTTPGet**：`path` = `path`，`port` = `IntOrString(port)`，**`scheme` 写死 `"HTTP"`**，再加 5 个阈值字段 |
| `2` | **TCPSocket**：`port` = `IntOrString(port)`，再加 5 个阈值字段 |
| 其它值 | **什么都不做**（三个 `if` 都不成立） |

⇒ 五个阈值字段（`initialDelaySeconds`/`timeoutSeconds`/`periodSeconds`/`successThreshold`/`failureThreshold`）
在 type=1/2 时**一律照设**（为 null 时也设 null ⇒ merge-patch 里不出现）。

## 4. 两处边界

1. `containers.get(0)`：容器列表为空 ⇒ `IndexOutOfBoundsException`，**厂商没有 try/catch 包住这段** ⇒ 走
   `Throwable` 兜底 ⇒ **500 `系统错误`/`code=-1`**（不是那两个 400 键）；
2. `HealthSetInfo.type` 是 `Integer`，厂商写的是 `if (readiness.getType() == 0)` ⇒ **拆箱**：
   `type` 为 **null 时 NPE** ⇒ 同样 500 `系统错误`。**我们主动收紧**（null ⇒ 不改该探针），
   记入 `docs/deviations.md`（与 B13/B15 同类）。

## 5. 验证计划

- **离线单测**（mock `IServiceRuntimePort`）：type=0 清空、type=1 的 httpGet（含 `scheme=HTTP`）与五个阈值、
  type=2 的 tcpSocket、其它 type 不动、readiness/liveness 各自独立（只传一个时另一个不动）、
  Deployment 不存在 ⇒ 400 `service.not.found.update.image.version`（带服务名）、
  patch 的 K8s 异常 ⇒ 400 `k8s.call.api.failed.to.change.image.version`；
- **在线独立命名空间**：造 Deployment（**两个容器**，`containers[0]` 预置一个探针），
  调端点后核对 `containers[0]` 的 readiness/liveness 形状、`containers[1]` **未被改动**，然后删命名空间。

## 6. 实现与实测结果（第 126 轮）

### 6.1 离线：`ServiceHealthCheckTest` 6 例，全量 **374** 条 0 失败

覆盖：两个探针各自透传；只传一个时另一个为 `null`（"不动"）；Deployment 不存在 ⇒ 400（带服务名）；
patch 的 K8s 异常 ⇒ 400 `k8s.call.api.failed.to.change.image.version`；
**`IndexOutOfBoundsException` 原样冒出去**（不 catch ⇒ 全局兜底 500 `系统错误`/`code=-1`，与厂商一致）。

> `type` → 探针形状的翻译在 infrastructure（`ServiceRuntimePort.toProbePatch`），所以**必须靠在线探针验**——
> 离线 mock 只能验"传了什么进去"。

### 6.2 在线：独立命名空间 `paas-verify-health`（Deployment **两个容器**，`containers[0]` 预置 `/old:9999` 的 readinessProbe）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | readiness `type=1`（path `/healthz`、port 8080、5 个阈值）+ liveness `type=2`（port 9090） | `200 data:true`；`containers[0].readinessProbe` 变成 **`httpGet{path:/healthz, port:8080, scheme:"HTTP"}`** + 5 个阈值（**旧的 `/old:9999` 被替换**）；`livenessProbe` = **`tcpSocket{port:9090}`** + 5 个阈值；**`containers[1]` 两个探针都仍为空** |
| ② | readiness `type=0`（清空）+ liveness `type=3`（未知） | `200 data:true`；`containers[0].readinessProbe` **被清空**；`livenessProbe` **保持 ① 的 `tcpSocket{9090}` 不变** ⇒ "其它 type 不动"实测确认 |
| ③ | Deployment 不存在 | `400 未找到该服务no-such-svc, 更新镜像版本失败`（`{0}` = 服务名） |

⇒ 三态（清空/HTTPGet/TCPSocket）+ "其它值不动" + "只改第一个容器" + 错误映射全部在真集群验到。

### 6.3 一处主动收紧（`deviations.md` B16）

`HealthSetInfo.type` 是 `Integer`，厂商写的是 `if (readiness.getType() == 0)` ⇒ **拆箱**：
`type` 为 **null 时 NPE** ⇒ 500 `系统错误`。我们按"**不改该探针**"处理（与 B13/B15 同类：
前端必传该字段，照抄等于故意留一个 NPE）。

### 6.4 收尾

验证命名空间、临时 NodePort、代理、后端进程全部回收；实验室数据未动（本轮不碰库）。

