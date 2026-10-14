# 服务域分诊（方法感知版）：修正上一版分类 + 新的可观测读端点

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14。

## 1. 修正：上一版把"非 GET"误判成了"参数类 400"

`findings/52` 里我把 `hostName`/`priority`/`node` 标为"400（参数类）"。**不准确**——它们根本不是 GET：

| 端点 | 真身 |
| --- | --- |
| `/v1/service/hostName` | **`PUT`**（`@RequestBody HostNameForm`）—— 改 Deployment |
| `/v1/service/priority` | **`POST`**（`serviceName`+`level`）—— 改 Deployment |
| `/v1/service/node` | **`DELETE`**（`?nodeName`）—— **删节点** |
| `/v1/service/servicePods` | **`DELETE`**（`@RequestBody DeletePodsForm`）—— **删 Pod** |

我用 GET 去打它们，拿到的 400 其实是"方法不对"的表现，我却归类成了"参数类"。
**教训**：分诊时**先看 HTTP 方法再看返回码**——同一个 400 在不同方法下含义完全不同。

其中 `node`（删节点）与 `servicePods`（删 Pod）**是破坏性写操作**，不在可验证范围内。

## 2. 本轮新发现的可观测**读**端点（GET，200）

| 端点 | 形状 | 备注 |
| --- | --- | --- |
| **`/v1/service/serviceInfoByNode?page&size(&nodeName&keyword&order)`** | `{runningDeployment,total,count,services}` | **与 `/v1/service` 同形** ⇒ 形状可直接复用已对拉的 DTO |
| `/v1/service/otherPlugin` | `{daemonSetList, deploymentList, statefulSetList}` | 3 键；值是 K8s 对象列表 |
| `/v1/service/restartInfo?serviceName` | `list(0)` | 本环境为空；形状要看 handler |
| `/v1/service/healthCheck` | 400 | 需更多参数（未查） |
| `/v1/service/schedulingNode?nodeName` | 400 | 需更多参数（未查） |

**下一轮首选 `serviceInfoByNode`**：形状同已对拉的列表端点，只需读它的 node 过滤逻辑（`ServiceController:1692-...`），
预期是"按 `spec.nodeName` 过滤 Pod → 归属到服务"，属于小改动且**可拿到非空对拉**。

## 3. 服务域剩余端点的性质归纳（到本轮为止）

- **已实现并对拉**：`/v1/service`、`labels`、`updateName`、`gaop`、`serviceAuth`（5/33）；
- **可观测读、待实现**：`serviceInfoByNode`（首选）、`otherPlugin`、`restartInfo`；
- **依赖缺失外部设施**：`imageStorage`（Harbor）；
- **内容退化**：`baseComponent`（2 条均 null）；
- **厂商自身坏掉**：`{serviceName}` 详情（NPE）、`event`/`baseEvent`（500）；
- **破坏性写**：`node`（删节点）、`servicePods`（删 Pod）、`batch-*`、`startUp`/`cancelStartUp` 等；
- **非破坏性写（需独立 namespace 验证）**：`hostName`、`priority`、`patch*`、`scale`、`rollingUpdate`、`updateServiceRole`、`nodePort`。

## 4. 状态

本轮零业务代码改动（只做方法感知分诊）；全量 **34 类 229 条全绿**；只读探测、无残留。
