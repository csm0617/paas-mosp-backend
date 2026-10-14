# `GET /v1/service/event` 规格 + 实现（第 130 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.getServiceEvent:2821-2859` 逐行读）+〔运行实测〕（真集群）。2026-10-14。

## 1. 端点

```java
@GetMapping("/event")
public Response<List<CoreV1Event>> getServiceEvent(@RequestParam String name)
```

前端用法：`url:"/v1/service/event", method:"get", params.name=e.name`（服务详情页的"事件"）。

## 2. 流程

```
try {
  1. 列出命名空间里的**全部** pod，只留 label `system/svcName == name` 的
  2. 对每个这样的 pod：
       podPreName = metadata.generateName **去掉最后一个字符**   ← generateName 形如 `nps-svc-6f8b5db694-`
       收集 podPreNameList、podNameList
  3. podPreNameList 为空 ⇒ 直接 Response.ok(空列表)（200）
  4. 用 podPreNameList.get(0) 查：involvedObject.name=<podPreName>, involvedObject.kind=ReplicaSet
  5. 对**每个** podName 查：involvedObject.name=<podName>, involvedObject.kind=Pod
  6. 按 lastTimestamp 排序：Comparator.comparing(lastTimestamp, nullsLast(natural)).reversed()
  7. 返回
} catch (ApiException e) → 400 `service.get.service.event.info.exception`
```

## 3. 三处要照抄的点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **只对"第一个" pod 查 ReplicaSet 事件** | 但 **Pod 事件是每个 pod 都查**——两者不对称（`podPreNameList.get(0)` vs 遍历 `podNameList`） |
| 2 | **`generateName` 去末位字符** | 不是去 `-` 后缀、也不是 split，就是 `substring(0, len-1)` |
| 3 | **排序 = 空值最前 + 时间降序** | 写的是 `nullsLast(...).reversed()`——`reversed()` 把"空值最后"也翻成了**空值最前**；照抄的是**实际效果**，不是字面语义 |

## 4. 一处主动收紧（`deviations.md` B18）

`metadata.generateName` 为 **null** 时，厂商直接 `generateName.substring(...)` ⇒ **NPE ⇒ 500**。
非控制器创建的 pod（裸 Pod）没有 `generateName`；我们**跳过该 pod**（其余照常），
所以"裸 Pod + 正常 Pod 混在一起"时不会整页 500。

## 5. 一处实现差异（结果集等价，已注明）

厂商用服务端 `fieldSelector`；**fabric8 7 的 events DSL 在 `inNamespace` 之后没有 `withFieldSelector`**
（`NonNamespaceOperation` 不带该方法），所以我们在**客户端**按 `involvedObject.name` + `involvedObject.kind`
过滤。两者的结果集相同，只是过滤发生在哪一侧。

## 6. 验证

### 6.1 离线：`ServiceEventTest` 5 例，全量 **393** 条 0 失败

覆盖：ReplicaSet 事件用**去末位字符**后的名字查、Pod 事件逐个查、时间降序；
**空 `lastTimestamp` 排最前**；没有匹配 pod ⇒ 空列表且**不查事件**；`generateName` 为 null 的 pod 被跳过；
K8s 异常 ⇒ 400 `service.get.service.event.info.exception`。

### 6.2 在线：独立命名空间 `paas-verify-event`（真集群）

前置（**注意：集群当时 `kube-controller-manager` 处于 `Error`/65 次重启的降级状态**，
新命名空间连 `default` ServiceAccount 都不会自动创建——我手工补了 SA 才建出 pod）：

- 手工建 Pod `generateName=nps-svc-abc-`、label `system/svcName=nps-svc`（服务端补全成 `nps-svc-abc-58x7r`）；
- 手工建 4 个 Event：`involvedObject={nps-svc-abc, ReplicaSet}` 两条（01:00、02:00）、
  `{nps-svc-abc-58x7r, Pod}` 一条（03:00）、以及一条**不该被命中**的 `{other-thing, Pod}`；
- 期间 controller-manager 短暂恢复，**自动**又建出了真实的 ReplicaSet `nps-svc-6f8b5db694` + Pod
  `nps-svc-6f8b5db694-n7t8f` 及其事件（`SuccessfulCreate`/`Scheduled`/`Pulling`）——正好成了"真事件"的对照组。

| 例 | 实测 |
| --- | --- |
| ① `?name=nps-svc` | `200 {"code":0}`，**6 条事件**：`Pod/nps-svc-6f8b5db694-n7t8f`(Pulling) → `ReplicaSet/nps-svc-6f8b5db694`(SuccessfulCreate) → `Pod/…-n7t8f`(Scheduled) → `Pod/nps-svc-abc-58x7r`(FailedMount/Scheduled/Started)。**ReplicaSet 只查了第一个 pod 的 RS**（`nps-svc-6f8b5db694`），而 **Pod 事件两个 pod 都有** ⇒ 第 3 节第 1 点的不对称实测确认；`other-thing` 的事件**被排除**；时间**严格降序** |
| ② `?name=no-such-svc` | `200`，`data: []`（没有匹配 pod 直接返回空） |

### 6.3 收尾

命名空间、临时 NodePort、后端进程全部回收；本轮不写库（实验室数据未动）。

**环境记录**：集群 `kube-controller-manager-k8s-master` 当时是 `Error`、**65 次重启**（14h），
`kube-scheduler` 也重启过 61 次；这解释了本会话前几轮反复出现的 API 抖动、`Terminating` 卡住、
以及新命名空间没有 `default` SA。**做在线验证前先看一眼这两个组件**，能省掉一轮排查。
