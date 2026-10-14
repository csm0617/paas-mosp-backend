# `PUT /v1/service/batch-restart` 规格 + 实现（第 134 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.serviceRestart:3826-3912` + `getPodNameByDeploymentName:3914-3931` 逐行读）
+〔运行实测〕（真集群独立命名空间 + 真库）。2026-10-14。

## 1. 端点

```java
@PutMapping("/batch-restart")
public Response<Boolean> serviceRestart(@RequestBody List<String> name)
```
body 是**服务名数组**；`[]` ⇒ 直接 `Response.ok(true)`。

## 2. 流程（**两条分支不对称**）

```
for 每个 serviceName：
   读 Deployment；抛异常 ⇒ 读 StatefulSet；再抛 ⇒ 记日志 + continue（跳过）
   ┌ StatefulSet 分支：
   │   给 pod 模板打注解 `kubectl.kubernetes.io/restartedAt` = LocalDateTime.now().toString()
   │   replaceNamespacedStatefulSet ⇒ continue        ← **不写任何库**
   └ Deployment 分支：
       podName = getPodNameByDeploymentName(serviceName)     // 按标签 system/svcName 找**第一个** pod
       if (podName != null) {
           exist = g_pod_event where service_name=? and pod_name=?
           restartNum = 1
           若 exist 非空：逐个取 max(exist.restartCount, restartNum)   ← 注意初值是 1
           插一条 g_pod_event {pod_name, service_name, create_at=now, restart_count=restartNum}
       }
       给 pod 模板打同样的注解 → replaceNamespacedDeployment
```

## 3. 照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **Deployment 先写库、再 replace** | replace 失败时那条 `g_pod_event` **已经落库**（顺序可观测；单测里专门断言了这一点） |
| 2 | **StatefulSet 完全不写库** | 只打注解 + replace（连 pod 名都不查） |
| 3 | `restartNum` 初值是 **1** | 不是 0：没有历史记录时新行 `restart_count = 1`；有历史时是 `max(1, 历史最大值)` |
| 4 | 注解值是 **`LocalDateTime.now().toString()`** | 形如 `2026-10-14T16:54:55.129188`（**不是**毫秒、也不是 ISO 带 Z 的串） |
| 5 | 找不到 pod 时**照样 replace** | 只是跳过"写记录"这一步 |
| 6 | 两个错误键 | 找 pod 失败 ⇒ 400 `service.get.service.info.exception`；replace 失败 ⇒ 400 `service.restart.name.failed`（带服务名） |

## 4. 实现与验证

**实现**：`IServiceQueryService.batchRestartServices` + 控制器端点；
新增端口 `replaceWorkloadRestartedAt`（打注解 + 整份 replace）；DAO/仓储新增
`selectPodEventsByServiceAndPod` + `insertPodEvent`（复用已有的 `readWorkloadState` 判断分支、`listPodsByLabel` 找 pod 名）。

### 4.1 离线：`ServiceBatchRestartTest` 7 例，全量 **420** 条 0 失败

覆盖：Deployment 分支先写库再 replace（断言 `restartNum = max(1,7) = 7`）、无历史时 `= 1`、
找不到 pod ⇒ 不写库但照样 replace、StatefulSet 分支不查 pod 也不写库、未知服务与空/null 入参跳过、
找 pod 失败 ⇒ `service.get.service.info.exception`、replace 失败 ⇒ `service.restart.name.failed`
（并断言那条记录**已落库**，锁住厂商的顺序）。

### 4.2 在线：独立命名空间 `paas-verify-restart` + 真库

前置：Deployment `dep-svc`、StatefulSet `sts-svc`、**手工建一个带 `system/svcName=dep-svc` 标签的 pod**
（`dep-svc-pod-1`）、以及 `g_pod_event` 里预置 `(dep-svc, dep-svc-pod-1, restart_count=5)`；另有 `nopod-svc`（无 pod）。

| 例 | 实测 |
| --- | --- |
| ① `["dep-svc","sts-svc","ghost"]` | `200 data:true`；Deployment 注解 = `2026-10-14T16:54:55.129188`、StatefulSet 注解 = `2026-10-14T16:54:56.371278`（**LocalDateTime 形态**）；`g_pod_event` **新增一行 `restart_count=5`**（= max(1,5)）、create_at 是当前毫秒；**`sts-svc` 没有产生任何新行** ⇒ 第 3 节 2/3/4 全部实测确认 |
| ② `["nopod-svc"]` | `200 data:true`；注解**照样写上**（`2026-10-14T16:55:15.672628`）；`g_pod_event` 里该服务 **0 行** ⇒ 第 3 节 5 实测确认 |

（① 前两次请求因集群 API 抖动返回 500，第三次成功——与 `findings/124` §6.3 记录的 etcd/控制面降级一致。）

### 4.3 收尾

探针 `g_pod_event` 行已删（剩余 3 行是 `gaop-*` 的**历史数据**，不是本轮的）；命名空间（含 Calico 引起的
`Terminating`，清 finalizer 后删除）、临时 NodePort、后端进程全部回收。
