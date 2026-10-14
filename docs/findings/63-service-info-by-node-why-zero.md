# `serviceInfoByNode` 为什么是 0：**单节点集群，且所有 Pod 都在 master 上**

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

**两者合起来就解释了 0**：这个端点回答的是"**某个工作节点上跑着哪些服务**"，
而本集群**只有一个节点，且它就是 master**；所有 Pod 都在 master 上 ⇒ 排除 master 之后**一条不剩**。

⇒ `total=0` **不是"没数据"，而是"正确地没有工作节点可归属"**。这满足我上一轮定的标准：
**能解释「为什么是 0」，这个 0 才算证据。**

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
