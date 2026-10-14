# `serviceInfoByNode` 为什么是 0：**没有任何工作负载带 `gaop/serviceLabel` 注解**

> **⚠️ 第 79 轮更正**：本文原先的解释是"单节点集群 + 全部 Pod 在 master ⇒ 排除 master 后为空"——
> 那是**推断**（原文 §3 也标了"必须读 `getServicesItems` 才能定论"）。本轮把 `getServicesItems` 读全了，
> **真因不是 master 节点**：过滤器要求 `gaop/serviceLabel` 注解**非空**，而本环境**没有任何工作负载带这个注解**
> （实测：只有 `kubectl.kubernetes.io/restartedAt`，其余为空）⇒ 一条都不留。
> 原文保留在下方以留痕。

**证据等级**：〔运行实测〕（集群事实）+〔代码事实〕。2026-10-14。本轮零业务代码改动。

## 1. 集群事实（决定性）

```
节点只有一个：k8s-master
  labels: node-role.kubernetes.io/control-plane=""

所有 Pod 都跑在 k8s-master 上：
  apichk               node=k8s-master  c0=apichk
  gaop-auth-…          node=k8s-master  c0=gaop-auth
  gaop-be-…            node=k8s-master  c0=gaop-be
  gaop-gateway-auth-…  node=k8s-master  c0=gaop-gateway-auth
  gaop-redis-…         node=k8s-master  c0=redis        ← 注意：容器名与工作负载名不同
  gaop-web-…           node=k8s-master  c0=gaop-web
```

## 2. 代码路径（`ServiceController:1699-1741`）

```
masterNodeName = k8sObjectUtil.getMasterNodeName(nodeRole)     // 本环境 = k8s-master
itemList = serviceInfoCache.get(k, -> getServicesItems(nodeName, keyword, masterNodeName))
getServicesItems: 列 Pod（nodeName 非空时按 spec.nodeName 过滤）
                  服务名 = **容器名**（containers.get(0).getName()）
                  …（后续按 masterNodeName 排除 master 上的 Pod）
```

**当时的推断**（**已被下面的第 79 轮更正取代**）：这个端点回答的是"某个工作节点上跑着哪些服务"，
而本集群只有一个节点且它就是 master，所以排除 master 后一条不剩。

### 第 79 轮更正：真因是**注解**，不是 master 节点

把 `getServicesItems` 读全之后，过滤器是：

```java
serviceNamed.contains(deployment.metadata.name)
  && annotations != null
  && annotations.get("gaop/serviceLabel") != null && !label.trim().isEmpty()
```

- `serviceNamed` 来自 **Pod 的容器名**（`serviceBelongPodList` 的键；例如 `gaop-redis` 的容器名是 `redis`）；
- 实测：**没有任何 Deployment 带 `gaop/serviceLabel`**（只有部分带 `kubectl.kubernetes.io/restartedAt`）
  ⇒ 第二个条件**恒不成立** ⇒ `serviceInfoByNode` 恒为空。

**master 节点那件事与本端点的空结果无关**——我第 55 轮的推断是错的（好在当时标了"待确认"，
并在 §4 写了"必须读 `getServicesItems` 才能定论"）。

⇒ 该端点在本环境**可以拿到"可解释的 0"对拉**，但前提是先把 `ServicesItem` 的装配
（`podInfoList` / `belongNodeList` / `mappingInfo`）读完——那部分**仍未读**。

## 3. 顺带发现一个实现关键点（容易漏）

**服务名取自"容器名"，不是工作负载名**。实测 `gaop-redis` 的容器名是 **`redis`**——
所以按容器名归集的服务集合与按 Deployment 名得到的集合**不一样**。
实现时必须照抄这个取法，否则 `serviceAuth`/`/v1/service` 那套"按 Deployment 名"的经验会直接套错。

## 4. 现在的定位与下一步

`serviceInfoByNode` 从"**参考退化**"升级为"**参考可解释**"：
- **能对拉**：本环境两侧都应为 0，且**能说明为什么**（单 master 节点）；
- **实现仍未做**：需要把 `getServicesItems` 剩余部分（Pod→服务的容器名归集、master 排除、
  `podInfoList` 的 CPU/内存取值）读到可翻译——这是**一个中等规模**的改动，不是小补丁；
- 因此下一轮的取舍是明确的：**要么投入一个整轮把它做完（含"0=0 可解释"的对拉）**，
  要么先转向服务域里"独立 namespace 可验证的写端点"。

## 5. 状态

全量 **34 类 229 条全绿**；本轮只读；无集群写入；无残留。
