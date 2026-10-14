# `GET /v1/service/serviceInfoByNode`：算法已读，但参考值是**退化的 0**

**证据等级**：〔代码事实〕+〔运行实测〕。2026-10-14。本轮零业务代码改动。

## 1. 实测：它与 `/v1/service` **不是同一份数据**

```
/v1/service                total=9   n=9
/v1/service/serviceInfoByNode?page=1&size=20   total=0   n=0
```

**两者集合完全不同**（`/v1/service` 有 9 个，它一个都没有）。所以上一轮我写的
"与 `/v1/service` 同形 ⇒ 形状可复用"**只对了形状那一半**；数据来源与过滤**是另一套**。

## 2. 已读完的算法（`ServiceController:1692-1745`）

```
params: nodeName(可选) page size keyword(>45→400) order(1..6)
1) masterNodeName = k8sObjectUtil.getMasterNodeName(nodeRole)   // 失败 → k8s.get.node.info.failed
2) 缓存键 = nodeName + "|" + keyword + "|" + masterNodeName
   itemList = serviceInfoCache.get(缓存键, k -> getServicesItems(nodeName, keyword, masterNodeName))
3) order 非空时排序：
   1/2 = CPU 升/降   （对 podInfoList 的 cpuMaxUsed 求和 /1000）
   3/4 = 内存 升/降  （memoryMaxUsed 求和 /1000）
   5/6 = gpuOrder 升/降
4) pagination(itemList, page, size)
```

**关键点**：数据由 `getServicesItems(nodeName, keyword, masterNodeName)` 产出，
**按节点归属**组织（还要排除 master 节点）——**这个方法我还没读**，它就是"为什么这里是 0"的答案所在。

## 3. 为什么 0（待确认的推断，不作为结论）

本环境 9 个工作负载全在同一个（master？）节点上。若 `getServicesItems` 的语义是
"列出**非 master 节点上**的 Pod 所归属的服务"，且节点上没有 `node-role` 之类的标签让
`getMasterNodeName` 正确识别，那么结果就可能是空——**但这是推断，必须读 `getServicesItems` 才能定论**
（这正是 `gaop` 那两轮教我的：不等读完不要下结论）。

## 4. 这个端点的定位

它属于**"可观测但参考退化"**一类（与 `baseComponent` 的 `{daemonSet:null,v1PodList:null}` 同级）：
- **能做**：实现它需要对 `getServicesItems` 的节点归属规则读到可翻译；
- **能验到什么程度**：本环境只能验"两边都是 0"——而按 `gaop` 的经验，**只有能解释"为什么是 0"，
  这个 0 才算证据**。所以下一轮的动作顺序是：**先读 `getServicesItems`，能解释 0 再实现**。

## 5. 对服务切片剩余工作量的判断（更新）

| 类别 | 数量 | 说明 |
| --- | --- | --- |
| 已实现且对拉 | 5 | `/v1/service`、`labels`、`updateName`、`gaop`、`serviceAuth` |
| 可观测但参考退化 | 3 | `serviceInfoByNode`(0)、`baseComponent`(null)、`restartInfo`(空 list) |
| 依赖缺失设施 | 1 | `imageStorage`（Harbor） |
| 厂商自身坏 | 3 | 详情(500 NPE)、`event`/`baseEvent`(500) |
| 写类 | 其余 ~21 | 破坏性的不验证；非破坏性的在独立 namespace 验证 |

**结论**：服务域"能拿到非空对拉"的存量**基本用完了**。再往下做，要么是"退化参考 + 能解释的 0"，
要么是"独立 namespace 里的写操作验证"，要么是"按代码实现 + 标注证据来源"。
这个判断本身是下一轮该先确认的事，不要再靠逐个端点试。

## 6. 状态

全量 **34 类 229 条全绿**；本轮只读探测、无集群写入、无残留。
