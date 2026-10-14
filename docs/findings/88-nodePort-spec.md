# `POST /v1/service/{serviceName}/nodePort` 实现规格（下一轮可直接开工）

**证据等级**：〔代码事实〕（逐行读全，~150 行）。2026-10-14。本轮**不写业务代码**。

> 它与已完成的 `POST /{serviceName}/node_port`（`findings/86`）**不是近重复**：
> 后者只改**主 Service**；这个端点还额外管理**另一个 `<name>-nodeport` Service**。
> （第 77 轮我只看头部时误判为"近重复"，第 80 轮的规模地图已纠正。）

## 1. 算法（`updateServicePort:482-670`）

```
0) name == null → 400 service.name.not.null；cfg == null → 400 service.config.not.null
   type 为空 → "ClusterIP"

1) 读主 Service(name)：为 null → 500 SystemException("设置NodePort失败：service为空")

2) 读 Deployment(name)；**任意异常回退读 StatefulSet(name)**；两者都失败 →
   500 SystemException("设置NodePort失败：该服务<name>无法获取deployment信息")
   （注意：这一步**有** StatefulSet 回退，与 node_port 的 A 分支**不同**）

3) containerPorts = (Deployment|StatefulSet).spec.template.spec.containers[0].ports
   若非空：逐个生成 V1ServicePort：
     appProtocol = protocol；name = "<protocol>-<name>-<i>" 小写
     nodePort = **null（显式置空）**；port = containerPort；protocol = protocol
     targetPort = IntOrString(containerPort)
   然后 service.spec.ports = 这批 ports

4) **service.spec.type = "ClusterIP"（写死的常量，不是 cfg.type！）**

5) merge-patch 主 Service

6) **仅当 cfg.type == "ClusterIP"** 才继续处理 `<name>-nodeport`：
   nodePortServiceName = name + "-nodeport"
   读它 → exist?
   nodePortUsed = getNodePortUsed()   // 本命名空间**所有 Service** 的 nodePort 集合

   6a) 不存在 → 造：
       serviceDoc = AppService.generateServiceDoc(deploymentName, labels, null)
       name = <name>-nodeport；type = **cfg.type**
       逐个 nodePort 配置：
         nodePort 为空 → **随机**生成 [10000, 75534]（`rand.nextInt(65535)+10000`），
                        最多试 10 次，命中 nodePortUsed 就重试
         否则 → 用它
         生成 V1ServicePort{appProtocol, name 公式, nodePort, port, protocol, targetPort=port}
       create Service → **return true**
         ApiException → 400 service.port.occupied.error
         其它异常   → 400 service.create.node.port.failed

   6b) 已存在 → 同样逐个生成端口（含上面的随机分配），patch 它 → **return true**

7) 走到这里（cfg.type != "ClusterIP" 且没有进入 6）→ **return false**

异常（外层 catch）：422 且响应体含 "provided port is already allocated" → 400 service.port.occupied；
其它 ApiException → 500 service.update.nodeport.failed
```

## 2. 四处**必须照抄**的怪点

1. **主 Service 的 type 写死 `ClusterIP`**，而 `<name>-nodeport` 用 `cfg.type` —— 同一请求里两个 Service 用不同来源；
2. **`nodePort` 为空时是随机分配**（[10000, 75534]，≤10 次重试）⇒ **这个端点的结果本身不可复现**：
   对拉时不能比 nodePort 的具体数值，只能比"结构 + 是否落在合法区间 + 不冲突"；
3. **`cfg.type != "ClusterIP"` 时返回 `false`**（不做 6，也不报错）；
4. `nodePort = null` 在步骤 3 是**显式**设置的（与"不设置"不同：merge patch 里会出现该字段为 null）。

## 3. 依赖的外部工具

- `AppService.generateServiceDoc(deploymentName, labels, null)` —— 造 Service 骨架（selector/labels 来自 Deployment）；
- `getNodePortUsed()` —— 列本命名空间所有 Service 收集 nodePort。

两者都要在实现里补（前者是"从 Deployment 生成 Service 模板"的纯函数，可离线测）。

## 4. 下一轮实现与验证计划

**实现**：gateway（读 Service/Deployment|StatefulSet、create/patch Service、列已用 nodePort）、
port、域服务（8 步）、控制器 `POST /v1/service/{serviceName}/nodePort` → `ResponseDTO<Boolean>`。

**独立 namespace 验证**（五步）：
1. 造 Deployment（带 containerPort）+ 主 Service → 调 `type=ClusterIP`：断言主 Service `type=ClusterIP`、
   ports 按容器端口推导（`nodePort=null`）、且 **`<name>-nodeport` 被创建**、其 type = 请求的 type；
2. 再调一次（`<name>-nodeport` 已存在）→ 断言走 6b 分支（patch 而非 create）；
3. 请求带显式 `nodePort` → 断言被采用；
4. 请求不带 `nodePort` → 断言生成值落在 **[10000, 75534]** 且不与已有冲突；
5. `type != "ClusterIP"` → 断言 `data:false` 且**没有**创建 `<name>-nodeport`。

## 5. 状态

本轮无业务代码改动；全量 **278 条用例全绿**；无残留资源。
