# `POST /v1/service/{serviceName}/nodePort`：实现了，并发现**这个端点在厂商侧无法成功**

**证据等级**：〔运行实测〕（独立 namespace + K8s 报错原文）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-nps`，用完即删）

| 用例 | 结果 |
| --- | --- |
| 主 Service 部分 | ✅ 正确：`type=ClusterIP`、ports 由容器端口推导（`tcp-nps-svc-0`、`nodePort=null`） |
| `type=ClusterIP` + 带 `nodePort` | 400 `service.port.occupied.error`。**K8s 原文**：`spec.ports[0].nodePort: Forbidden: may not be used when 'type' is 'ClusterIP'` |
| `type=ClusterIP` + 不带 `nodePort` | 400 同上。**K8s 原文**：`spec.ports: Required value` |
| `type=LoadBalancer` | 200 `data:false`（照抄：不进创建分支） |

## 2. 结论：这个端点在厂商侧**在任何可达输入下都无法成功**

厂商代码：只有 `serviceConfig.type == "ClusterIP"` 时才进创建分支（第 6 步），而创建时
`serviceDoc.spec.type = serviceConfig.getType()` ⇒ **一定是 ClusterIP**；同时

- 若 `nodePorts` 非空 → 端口里带 `nodePort` ⇒ K8s **422**（ClusterIP 不允许 nodePort）；
- 若 `nodePorts` 为空 → 根本不设置 `spec.ports` ⇒ K8s **422**（`spec.ports: Required value`）；
- 若 `type != "ClusterIP"` → 直接 `return false`。

⇒ **该端点永远拿不到 `true`**。而且那个"端口已被占用"的键（`service.port.occupied.error`）
在这里是**误报**：真实原因是"ClusterIP 不允许 nodePort"，不是端口占用。

这与之前记录过的厂商缺陷同族（`updateServiceRole` 写死代码、`/v1/service/{name}` NPE）：
**前端调这个端点只会看到 400 或 false**。我们**照抄**其可观测行为（键名、布尔、K8s 报错映射），
不做"顺手修正"——否则就与厂商不一致了。

## 3. 实现（照抄的部分）

- 主 Service：Deployment **优先、StatefulSet 回退**（与 `/node_port` 的 A 分支不同，后者无回退）；
  `type` **写死 `ClusterIP`**；端口里 `nodePort` **显式置 null**；
- `<name>-nodeport`：不存在则按 `generateServiceDoc` 骨架创建（labels `name/system/appName/system/svcName/system/appType`、
  annotations `system/lbgroup=none`、**selector `name=<工作负载名>`**），存在则 patch；
- `nodePort` 为空时**随机**取 [10000, 75534]（≤10 次重试避开已用端口）；
- 异常映射：K8s API 异常 → 400 `service.port.occupied.error`；其它 → 400 `service.create.node.port.failed`。

**分层说明**：domain 不依赖 fabric8（分层要求），所以由 infrastructure 的 port 把
`KubernetesClientException` 翻译成领域可见的 `K8sApiException`（新增 types 异常），domain 只认后者。

## 4. 单测发现的一个实现缺陷（已修）

`listUsedNodePorts` 返回的集合被直接 `add`（端口分配要写进去）——测试用不可变列表暴露了它。
已改为**先复制再改**（`new ArrayList<>(...)`）。这类"依赖调用方返回可变集合"的写法在真实调用链里
通常不会立刻出错（gateway 返回 ArrayList），但属于隐患。

## 5. 状态

服务切片 **15/33**；测试 **41 类 283 条全绿**（上一轮 278）。
临时 namespace 已删（NotFound）、NodePort 已删、进程清零、工作树干净。
