# `GET /v1/service/baseEvent` 规格 + 实现（第 132 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.getBaseServiceEvent:3377-3421` 逐行读）+〔运行实测〕（真集群 kube-system）。2026-10-14。

## 1. 端点

```java
@GetMapping("/baseEvent")
public Response<List<CoreV1Event>> getBaseServiceEvent(@RequestParam String baseServiceName)
```

用途：基础组件（kube-system 里的 kube-proxy / kube-dns / 控制面组件）的"事件"页签。

## 2. 流程

```
1. labelSelector = "kube-proxy".equals(baseServiceName) ? "k8s-app=" + baseServiceName : "name=" + baseServiceName
2. 列 **kube-system** 里匹配的 pod（命名空间**写死**）
   ApiException ⇒ 400 `service.get.basic.service.container.info.failed`
3. podNameList = 这些 pod 的 metadata.name
4. ReplicaSet 名 = **baseServiceName + "-"**（直接用，**不是**从 pod 的 generateName 推）
5. 查 kube-system 里 involvedObject.name=<rs>, kind=ReplicaSet 的事件
   ApiException ⇒ 400 `service.get.container.event.info.failed`
6. 对每个 podName 查 involvedObject.name=<pod>, kind=Pod 的事件
   ApiException ⇒ 400 `service.get.container.pod.event.info.failed`
7. 按 lastTimestamp **降序**：Comparator.comparing(CoreV1Event::getLastTimestamp).reversed()
8. 返回
```

## 3. 与 `GET /v1/service/event`（`findings/123`）的四处不同

| # | 差异 | `/event` | `/baseEvent` |
| --- | --- | --- | --- |
| 1 | 命名空间 | 请求上下文里的 | **写死 `kube-system`** |
| 2 | 标签选择器 | `system/svcName=<name>` | `kube-proxy` ⇒ `k8s-app=<name>`，**其它** ⇒ `name=<name>` |
| 3 | ReplicaSet 名 | 第一个 pod 的 `generateName` **去末位字符** | **直接** `<baseServiceName>-` |
| 4 | 排序 | `nullsLast(...).reversed()`（空值**最前**） | `.reversed()`（**没有空值保护**） |

## 4. 一处主动收紧（`deviations.md` B19）

第 4 点那个"没有空值保护"是**真的会炸**：`Comparator.comparing(getLastTimestamp)` 遇到 `lastTimestamp == null`
的事件时，比较器内部抛 **NPE** ⇒ 未捕获 ⇒ 500 `系统错误`。
我们按"**时间降序、空值排最后**"处理（该端点的意图显然是"最新在前"，空值只是没有时间信息）。

## 5. 实现与验证

**实现**：`IServiceQueryService.queryBaseServiceEvents(baseServiceName)` + 控制器端点。
**不需要任何新端口方法**——复用 `/event` 那两个（`listPodsByLabel`、`listEvents`，只是命名空间与键值不同）。

### 5.1 离线：`ServiceBaseEventTest` 6 例，全量 **405** 条 0 失败

覆盖：`kube-proxy` 用 `k8s-app` 而其它用 `name`；ReplicaSet 名直接用 `<name>-`；时间降序且**空值排最后**；
**没有 pod 也不短路**（照样查 ReplicaSet 事件）；三个错误键各自的分工；全空 ⇒ 空列表。

### 5.2 在线：真集群 `kube-system`（**不建命名空间**，只造 4 条探针事件，验完删）

**先看真实数据**：kube-system 里只有控制面 pod 的 `component=` 标签与 coredns 的 `k8s-app=kube-dns`——
⇒ 厂商那个 `name=<x>` 选择器在本集群**匹配不到任何控制面 pod**（它们的标签是 `component=`），
所以只能靠 `<name>-` 的 ReplicaSet 分支拿到数据；而真实数据里没有 ReplicaSet 事件。于是造探针事件：

| 探针事件（`involvedObject.name=kube-proxy-, kind=ReplicaSet`） | 结果 |
| --- | --- |
| `ProbeNew`（04:00）、`ProbeOld`（02:00）、`ProbeNoTs`（**无 lastTimestamp**） | 全部命中 |
| `ShouldBeFilteredOut`（`involvedObject.name=something-else`） | **被排除** |

`GET /v1/service/baseEvent?baseServiceName=kube-proxy` 实测：
```
200 {"code":0}，3 条
  2026-10-14T04:00:00Z  ReplicaSet/kube-proxy-  ProbeNew
  2026-10-14T02:00:00Z  ReplicaSet/kube-proxy-  ProbeOld
  None                  ReplicaSet/kube-proxy-  ProbeNoTs      ← 空值排最后（B19）
```
⇒ "降序 + 空值最后 + 按 involvedObject 过滤"三条实测确认。验完 4 条探针事件已删除（`grep probe-be` = 0）。

### 5.3 收尾

探针事件已删、临时 NodePort 已删、后端进程已停；本轮不写库（实验室数据未动）。
