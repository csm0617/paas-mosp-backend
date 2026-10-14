# `POST /v1/service/{serviceName}/node_port` 实现规格（下一轮可直接开工）

**证据等级**：〔代码事实〕（逐行读全）。2026-10-14。本轮**不写业务代码**：把 120 行的处理器读到"可直接翻译"。

## 1. 端点与返回

```
POST /v1/service/{serviceName}/node_port
body: ServiceConfig { nodePorts: [NodePortConfig{port, protocol, nodePort}], type }   // type 默认 "ClusterIP"
返回: Response<Boolean> —— 实现是 `Response.ok(v1Service != null)`
```
⇒ 成功时 `data:true`；**返回的是布尔，不是 Service 对象**（与 `scale` 那个返回整份对象的端点不同）。

## 2. 算法（`patchServicePorts:701-815`，逐行）

```
0) name == null        → 400 service.name.not.null
   serviceConfig == null → 400 service.config.not.null
   type 为空 → 设为 "ClusterIP"
   nodePorts = serviceConfig.nodePorts

A) 若 type == "NodePort" 且 (nodePorts == null 或空)：
   读 Deployment(name)：
     为 null → 500 SystemException("设置NodePort失败：该服务<name>无法获取deployment信息")   ← 消息里带服务名
   读 Service(name)：
     为 null → 500 SystemException("设置NodePort失败：service为空")
   ports = deployment.spec.template.spec.containers[0].ports
   若 ports 非空：逐个 containerPort 生成 V1ServicePort：
     appProtocol = containerPort.protocol
     name        = "<protocol>-<name>-<i>" 的 **小写**
     port        = containerPort.containerPort
     protocol    = containerPort.protocol
     targetPort  = IntOrString(containerPort.containerPort)
   service.spec.ports = portList; service.spec.type = type
   **merge-patch Service** → 返回 patch 后的对象
   （若 ports 为空 → 什么都不做，直接落到函数末尾的 return，返回 null ⇒ 控制器给 data:false）

B) 否则（一般分支）：
   读 Service(name)：为 null → 500 SystemException("设置NodePort失败：service为空")
   ports = []
   若 type == "NodePort"：逐个 NodePortConfig 生成 V1ServicePort：
     appProtocol = protocol; name = "<protocol>-<name>-<i>" 小写
     nodePort = cfg.nodePort; port = cfg.port; protocol = cfg.protocol
     targetPort = IntOrString(cfg.port)
   service.spec.ports = ports; service.spec.type = type
   **merge-patch Service** → 返回 patch 后的对象

异常映射（两个分支共用）：
   ApiException 且 code == 422 且响应体含 "provided port is already allocated"
       → 400 service.port.occupied
   其它 ApiException → 500 SystemException("service.update.nodeport.failed")
```

## 3. 三个容易写错的点

1. **`name` 里的 `<i>` 是下标**，且整体 `.toLowerCase()`（例如 `tcp-gaop-web-0`）；
2. **两个分支的错误消息不同**：A 分支"读不到 Deployment"用的是**带服务名的中文消息**（`SystemException` 的参数就是消息本身），
   而 B 分支只有 `service为空` 那条；
3. **A 分支里 `ports` 为空时会返回 null ⇒ `data:false`**（不是 true、也不是报错）——这是"端口推导不出来"的静默失败。

## 4. 同族的另一个端点

`POST /v1/service/{serviceName}/nodePort` → `updateServicePort(name, cfg)`：头部校验与 A 相同
（`name`/`cfg` 判空、`type` 默认 `ClusterIP`），**本轮只读到头部**，未逐行读完；
从命名与结构看是同一族的近重复实现，**实现时不要把两者混为一谈**（返回类型与分支细节可能不同）。

## 5. 下一轮的实现与验证计划

**实现**（按现有分层）：
- `ServiceRuntimeGateway`：`readDeploymentPorts(ns,name)`、`readService(ns,name)`、`patchServicePorts(ns,name,type,ports)`；
- `IServiceRuntimePort` + 实现：把 K8s 对象翻成领域对象（`ServicePortSpecVO`）；
- 域服务：按 §2 的两个分支实现，错误用现有 `SystemException`/`WrongArgumentException`；
- 控制器：`POST /v1/service/{serviceName}/node_port`，返回 `ResponseDTO<Boolean>`。

**验证**（独立 namespace，本会话已跑通六次）：
1. 造一个 Deployment（含 1-2 个 `containerPort`）+ 同名 ClusterIP Service；
2. 调 `node_port`，断言：Service 的 `type` 变成 `NodePort`、`ports` 按公式生成（**name 小写、下标、targetPort**）；
3. 造"没有 containerPort"的 Deployment → 断言返回 **`data:false`**（A 分支的空 ports 静默路径）；
4. 不存在的服务 → 断言 400/500 与厂商文案；
5. 端口被占用（同一 nodePort 用两次）→ 断言 400 `service.port.occupied`（文案对拉需谨慎：会动到 Service）。

## 6. 状态

本轮无业务代码改动；全量 **273 条用例全绿**；无残留资源。
