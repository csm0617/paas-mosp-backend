# `PUT /v1/service/batch-stop` / `batch-start` 规格 + 实现（第 133 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.serviceStop:3649-3685` / `serviceStart:3689-3740` /
`getServiceStatusByServicesItem:3742-3792` / `scaleDeployment|scaleStatefulSet:3794-3824` 逐行读）
+〔运行实测〕（真集群独立命名空间）。2026-10-14。

## 1. 两个端点

```java
@PutMapping("/batch-stop")   public Response<Boolean> serviceStop(@RequestBody List<String> name)
@PutMapping("/batch-start")  public Response<Boolean> serviceStart(@RequestBody List<String> name)
```
body 是**服务名数组**；`[]` ⇒ 直接 `Response.ok(true)`（不碰集群）。

## 2. `batch-stop`（看 `spec.replicas`）

```
for 每个 serviceName：
   读 Deployment；抛异常 ⇒ 读 StatefulSet；再抛 ⇒ 记日志 + continue（**跳过**）
   spec.replicas == 0 ⇒ 记日志 + continue（已经停了）
   否则 scale(workload, 0, "停止")
```

## 3. `batch-start`（看 `status` 推出来的状态串）

```
for 每个 serviceName：
   读 Deployment；抛异常 ⇒ 读 StatefulSet；再抛 ⇒ 记日志 + continue
   status = getServiceStatusByServicesItem(workload.status)
   "STOP"           ⇒ scale(workload, 1, "启动")
   "RUNNING"        ⇒ 记日志 + continue
   " ROLLING_UPDATE"⇒ 记日志 + continue      ← **前导空格**
   "STARING"        ⇒ 记日志（厂商拼写，不是 STARTING）
```

## 4. 状态推导的两个重载（**条件不同，这是最容易写错的地方**）

`getServiceStatusByServicesItem(V1DeploymentStatus)` 与 `(V1StatefulSetStatus)` 字段集一样，
但 **Deployment 那份多要求 `unavailableReplicas != null`**：

| 分支 | Deployment | StatefulSet |
| --- | --- | --- |
| `availableReplicas != null` 且四个字段都有 | `unavailable != null && ready > available` ⇒ `" ROLLING_UPDATE"`，否则 `"RUNNING"` | `ready > available` ⇒ `" ROLLING_UPDATE"`，否则 `"RUNNING"` |
| `availableReplicas == null` | `ready&&replicas&&updated&&unavailable` 全非空 ⇒ `RUNNING`；`replicas&&updated&&unavailable` 非空 ⇒ `STARING` | `ready&&replicas&&updated` 全非空 ⇒ `RUNNING`；`replicas&&updated` 非空 ⇒ `STARING` |
| 其它 | `"STOP"` | `"STOP"` |

（StatefulSet 根本没有 `unavailableReplicas` 字段，所以那一列天然少一个条件。）

## 5. `scaleDeployment` / `scaleStatefulSet`（两处照抄点）

1. 用 **`replaceNamespaced*`（整份 PUT replace）**，**不是 patch**；
2. 失败 ⇒ 400 `service.operation.failed`，`{0}` 是**中文动作词**（`"停止"` / `"启动"`）。

## 6. 一处主动收紧（`deviations.md` B20）

厂商写的是 `Integer replicas = spec.getReplicas(); if (replicas == 0)` ⇒ `spec.replicas` 为 **null 时拆箱 NPE**。
API Server 会给 Deployment/StatefulSet 默认 `replicas`，所以实际不可达；我们用 `Integer.valueOf(0).equals(...)`
（null 视为"不是 0"，继续执行 scale）。

## 7. 验证

### 7.1 离线：`ServiceBatchStartStopTest` 8 例，全量 **413** 条 0 失败

覆盖：停止只在 `spec.replicas != 0` 时下发、已停则跳过、未知服务跳过、空/null 入参直接 true；
启动只在 `STOP` 时下发、`RUNNING`/`" ROLLING_UPDATE"`/`STARING` 都跳过；
**Deployment 与 StatefulSet 在 `unavailableReplicas` 为 null 时的差异**（Deployment ⇒ `RUNNING`，StatefulSet ⇒ `" ROLLING_UPDATE"`）；
StatefulSet 状态全空 ⇒ `STOP`；失败键与中文动作词。

### 7.2 在线：独立命名空间 `paas-verify-batch`

前置：`run-svc`（Deployment，replicas=2）、`stopped-svc`（Deployment，replicas=0）、
`sts-svc`（StatefulSet，replicas=0）、`multi-svc`（Deployment，replicas=3）。

| 例 | 实测 |
| --- | --- |
| ① `batch-stop ["run-svc","stopped-svc","ghost"]` | `200 data:true`；**`run-svc` 2→0**；`stopped-svc` 保持 0（跳过）；`ghost` 跳过 |
| ② `batch-start ["stopped-svc","sts-svc","run-svc"]` | `200 data:true`；**`stopped-svc` 0→1**（Deployment 分支）、**`sts-svc` 0→1**（StatefulSet 分支）、`run-svc` 0→1（此刻状态已是 `STOP`） |
| ③ `batch-start []` | `200 data:true`，集群未动 |
| ④ `multi-svc` 的 status 手工置成 `replicas/ready/available/updated = 3` | `batch-start` 后 **`spec.replicas` 仍是 3** ⇒ 状态推导为 `RUNNING` ⇒ **跳过**（没有把它强制成 1） |

> ④ 值得说明：本集群的 controller-manager 在反复重启，Deployment 的 `status` 常被清空
> ⇒ 直接调 `batch-start` 会落进 `STOP` 分支（反而"启动"了）。所以我用
> `kubectl patch deploy … --subresource=status` **手工摆出 RUNNING 形状**再调，才把"跳过"这一支验实。

### 7.3 收尾

命名空间（含 Calico 引起的 `Terminating` 卡顿，清 finalizer 后删除）、临时 NodePort、后端进程全部回收；
本轮不写库（实验室数据未动）。
