# `POST /v1/service/tolerance` 规格 + 实现（第 131 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.allowMasterTolerance:3425-3475` + `ResponseExceptionConfig.i18n` 逐行读）
+〔运行实测〕（真集群，含一次**发现并修掉的序列化坑**）。2026-10-14。

## 1. 端点

```java
@PostMapping("/tolerance")
public Response<Boolean> allowMasterTolerance(@RequestParam String serviceName, @RequestParam Integer type)
```

## 2. 流程

```
Thread.sleep(100)                                    ← 厂商开头的固定延时（见 §4）
1. 读 Deployment(serviceName)：ApiException ⇒ 400 `service.not.found.update.taintservice.not.found.update.taint`（带服务名）
2. type == 0（关闭）：
     现有 tolerations 为空 ⇒ **直接 return true（不打 patch）**
     否则 ⇒ 整份置空后 merge-patch ⇒ true；patch 失败 ⇒ 400 `service.update.service.failed`（带服务名）
3. type == 1（开启）：
     整份置成**那一条**固定容忍后 merge-patch ⇒ true；patch 失败 ⇒ 同上
4. 其它 type ⇒ `Response.ok(false)`  ⇒ **HTTP 200 + data:false，且不打 patch**
```

固定的那一条容忍：`{key: "node-role.kubernetes.io/master", operator: "Exists", effect: "NoSchedule"}`。

## 3. 照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **那个拼重了的 i18n 键** | 厂商源码写的是 `"service.not.found.update.taintservice.not.found.update.taint"`（键名重复了两遍）。消息包里只有 `service.not.found.update.taint` ⇒ 查不到 ⇒ 厂商 `i18n()` 的 catch **回退成键本身**，所以响应 `msg` 就是这串拼重文本。我们的 `MessageResolver` 回退逻辑相同 ⇒ **照抄键名即可让文案一致** |
| 2 | **`type=0` 且本来就为空 ⇒ 不打 patch** | 直接 `data:true`；不是"总是打一次空 patch" |
| 3 | **其它 type ⇒ `data:false`（200）** | 不是 400；`type` 为 null 也走这条 |
| 4 | **整份替换 tolerations** | 不是追加：开启时把原有容忍**全部换掉** |

## 4. 未照抄的一处

厂商方法开头有 `Thread.sleep(100)`。它对响应体/状态码**无可观测影响**（只影响延迟），我们不加这 100ms。
已在 `IServiceQueryService.allowMasterTolerance` 的 javadoc 里注明。

## 5. 实测发现的一个真坑：**merge-patch 的"清空"打不出去**

第 131 轮在线验证时 `type=0`（清空）**打不动**——响应 200 但集群里容忍还在。逐层查下来：

1. 我原来的写法是"读对象 → `setTolerations(List.of())` → `Serialization.asJson(整份对象)` → JSON_MERGE patch"；
2. 写了个小程序实测：**`Serialization.asJson` 会把空集合（以及 null）省略掉**
   ——`Serialization.asJson(带空 tolerations 的 Deployment)` 里**根本没有 `tolerations` 键**；
3. merge-patch 的语义是"没出现的键不动" ⇒ 于是这次"清空"变成了 no-op；
4. 反证：`kubectl patch --type=merge -p '{"spec":{"template":{"spec":{"tolerations":[]}}}}'` **能**清掉
   ——说明服务端没问题，是我们发的体里没这个键。

**修法**：不再整份序列化，改成**显式构造 merge-patch 体**
`{"spec":{"template":{"spec":{"tolerations":[…]}}}}`（用 `Map` + `Include.ALWAYS`），
实测修好后 `type=0` 能把容忍清空。

> **可复用的教训**：凡是"用 merge-patch **清空/置空**一个字段"的地方，**不要**依赖"整份对象序列化"——
> 序列化器的 inclusion 策略会把空集合/空值吞掉，于是 patch 体里少了那个键、清空动作静默失效。
> 显式构造 patch 体（哪怕只有一层）是唯一可靠的做法。

**顺带复核了同类端点**：`PUT /v1/service/healthCheck` 的 `type=0`（清空探针）**在线复测仍然正常**
（先设 `/h:80` 的 httpGet，再 `type=0` ⇒ 集群里探针消失）；`updateVolumes` 用的是 **PUT replace**（不是 patch），
"整份替换"语义由服务端保证 ⇒ 不受这个坑影响。

## 6. 验证

### 6.1 离线：`ServiceToleranceTest` 6 例，全量 **399** 条 0 失败

覆盖：`type=1` 写入那一条固定容忍；`type=0` 且为空 ⇒ 返回 true 且**不调 patch**；
`type=0` 且非空 ⇒ 传空列表（整份置空）；`type=9` 与 `type=null` ⇒ `false` 且不调 patch；
Deployment 不存在 ⇒ 400 且键是**拼重的那串**（并断言参数是服务名）；patch 失败 ⇒ 400 `service.update.service.failed`。

### 6.2 在线：独立命名空间 `paas-verify-tol`（真集群）

| 例 | 实测 |
| --- | --- |
| ① `type=0`（本来就没有容忍） | `200 {"code":0,"data":true}`；集群里 tolerations **仍为空**（没打 patch） |
| ② `type=1` | `200 data:true`；`tolerations=[{"effect":"NoSchedule","key":"node-role.kubernetes.io/master","operator":"Exists"}]` |
| ③ `type=0`（关闭） | 修前：200 但**容忍还在**（no-op）；**修后：`tolerations=[]`** ✅ |
| ④ `type=9` | `200 data:false`，集群未变 |
| ⑤ Deployment 不存在 | `400`，`msg = service.not.found.update.taintservice.not.found.update.taint`（**拼重文本实测一致**） |

### 6.3 环境状态（本轮特别差）

集群的 **etcd 不健康**：`kubectl get --raw /healthz` 报 `[-]etcd failed: reason withheld`，
`kube-controller-manager-k8s-master` 处于 `Error`（65 次重启），`kube-scheduler` 重启 61 次。
表现是 API 间歇性 10s 超时（同一次 `type=1` 请求：前两次 500、第三次 200）。
`kubectl delete namespace` 依旧卡 `Terminating`（Calico discovery 陈旧），清 finalizer 后删除。
**这些都是集群自身状态，与本次实现无关**；但也意味着"在线验证要重试"应成为常态做法。
