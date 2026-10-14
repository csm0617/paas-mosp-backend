# `PUT /v1/service/patchService` 规格（第 123 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.updateServiceDiscovery:1014-1163` 逐行读 + 相关 form/mapper/DDL）。2026-10-14。本轮**读全规格 + 实现 A 分支**。

## 1. 端点与入参

```java
@PutMapping("/v1/service/patchService")
public Response<Object> updateServiceDiscovery(@RequestBody UpdateServiceDiscoveryForm form)
```

`UpdateServiceDiscoveryForm`（`controller/form/service/`）：`type`、`serviceName`、`portList`。
`UpdatePortItem`：`containerPort`、`protocol`、`name`、`port`、`targetPort`（**注意有三个"端口"字段，别混**）。

⇒ 这个端点**按 `type` 分两条完全不同的路径**：`type == "Service"` 走 **K8s 只读+patch**（改服务发现的 ClusterIP/端口），
否则走 **DB 写 `g_service_port` + patch 工作负载容器端口**。前端就是靠这个 `type` 区分两个页面动作。

## 2. A 分支：`type == "Service"`（**本轮已实现**）

```
读 Service(serviceName)；读不到 ⇒ ApiException(404)
读 Deployment(serviceName)；抛异常 ⇒ 读 StatefulSet(serviceName)；再抛 ⇒ 抛 SystemException("设置NodePort失败：该服务<name>无法获取deployment信息")
containerPorts = 第一个容器的 ports
（顺带读 <name>-nodeport 的 ports 赋给局部变量 ports —— **死代码，后面从没用过**）
p = 对每个 containerPort i：
      appProtocol = cp.protocol
      name        = "<cp.protocol>-<serviceName>-<i>".toLowerCase()
      nodePort    = null
      port        = cp.containerPort
      protocol    = cp.protocol
      targetPort  = IntOrString(cp.containerPort)
service.spec.ports = p；service.spec.type = "ClusterIP"
把**整份 service** 序列化成 JSON，application/merge-patch+json 打回去
return Response.ok(true)
```

### 2.1 错误映射（**最容易被"照抄"抄错的地方**）

厂商外层是 `catch (ApiException e) → 400 k8s.call.api.failed` / `catch (Exception e) → 400 service.update.service.discovery.failed`，
而它内层抛的 `SystemException`（500 语义）**被外层 `catch (Exception)` 吃掉** ⇒ 实际响应是 400：

| 情形 | 厂商实际响应 | 为什么 |
| --- | --- | --- |
| `serviceName` 的 Service 不存在 | **400 `k8s.call.api.failed`** | fabric8 的 `readNamespacedService` 抛 `ApiException(404)`（**不是**返回 null），被外层 ApiException 分支接住。⇒ 源码里那句 `service == null → SystemException("设置NodePort失败：service为空")` 是**死代码** |
| Deployment 与 StatefulSet 都不存在 | **400 `service.update.service.discovery.failed`** | 内层 `SystemException` 被外层 `catch (Exception)` 吞掉，500 文案**永远发不出去** |
| 其它异常（如 `spec`/`containers` 为 null 的 NPE） | **400 `service.update.service.discovery.failed`** | 同上 |
| 成功 | `200 {code:0, data:true}` | `Response.ok(true)` |

### 2.2 与已实现端点的关系（**A 分支不需要任何新端口方法**）

`/nodePort`（`IServiceQueryService.updateServiceNodePortsEndpoint`，`findings/88`）的**第 1–5 步就是 A 分支**：

| A 分支步骤 | 已有能力 |
| --- | --- |
| 判 Service 是否存在 | `IServiceRuntimePort.serviceExists` |
| 读 Deployment/StatefulSet 第一个容器端口（含回退） | `IServiceRuntimePort.readWorkloadContainerPorts`（两者都没有 ⇒ null） |
| 按容器端口造 `ServicePortSpecVO`（`name` 小写拼接、`appProtocol`、`targetPort`） | `updateServiceNodePortsEndpoint` 里同一段代码 |
| 整份 merge-patch `spec.ports`/`spec.type` | `IServiceRuntimePort.patchServicePorts` |

⇒ A 分支 = **"判存在 → 读容器端口 → 造 specs → patch ClusterIP"**，四步全部有现成实现；
唯一要新增的是**把异常翻成厂商那两个 400 键**。

## 3. B 分支：`type != "Service"`（**本轮只写规格，下一轮实现**）

```
读 Deployment(serviceName)；ApiException ⇒ 读 StatefulSet；再 ApiException
     ⇒ 抛 SystemException("修改dployment/statefulSet失败，找不到该服务")   ← 这个在 try **外面** ⇒ 真的 500
try {
  portList = form.portList.map(getPort)                      // 只取 port 字段
  deploymentName = (isDeployment ? deployment : statefulset).metadata.name
  softwareApps = select g_software_app where app_name = deploymentName
  if (!softwareApps.isEmpty()) {
      for (softwareId : softwareApps.softwareId) {
          delete g_service_port where software_id = softwareId and service_name = deploymentName
          insertBatch g_service_port：每个 port 一行
              {software_id, service_name = deploymentName, port,
               path_name = deploymentName + "-" + port, web_entry = false, allow = true, updated_at = now}
      }
      softwareService.notificationGateway()                  // 异步 GET http://<gateway.host>:<gateway.port>/refresh
  }
  containers = 该工作负载的 spec.template.spec.containers
  containers.forEach(c -> c.setPorts(portList.map(p -> V1ContainerPort(protocol=p.protocol, containerPort=p.containerPort))))
  merge-patch 整份 Deployment 或 StatefulSet
} catch (ApiException e) → 400 service.update.service.config.error
  catch (Exception e)    → 400 service.update.service.details.failed
return Response.ok(true)
```

四处要照抄的细节：
1. **`path_name` = `<deploymentName>-<port>`**，`allow` 恒 `true`、`web_entry` 恒 `false`（表单里的 `allow`/`webEntry` **不读**）；
2. **`g_software_app` 为空时**：不写库、不通知网关，但**仍然 patch 工作负载**（容器端口照样改）；
3. `containers.forEach(...)` 把**所有**容器（不只第一个）的 `ports` 都设成同一份列表；
4. 工作负载读不到是 **500**（`SystemException`，且在 try 外），与 A 分支的 400 形成对照——**同一个端点两条分支的错误语义不同**。

### 3.1 B 分支要新增的东西

| 缺什么 | 说明 |
| --- | --- |
| 读工作负载**名字**（Deployment/StatefulSet 谁在 + `metadata.name`） | 现有 `readWorkloadContainerPorts` 只返回端口，不返回名字与种类 |
| patch 工作负载容器端口 | 无现成方法（现有 patch 都是 hostname/priority/replicas/command/启动） |
| `g_software_app` 按 `app_name` 查 | `ISoftwareRepository` 现有的是按 softwareId 删/查，**没有按 app_name 查** |
| `g_service_port` 按 (softwareId, serviceName) 删 + 批量插 | 现有只有按 softwareId 删、按 id 改 path_name |
| 网关刷新通知（`GET /refresh`，异步、失败不影响响应） | 全新端口；厂商 `SoftwareService.notificationGateway:2287-2294` |

## 4. 本轮（123）实现范围与验证

- **实现 A 分支**：`IServiceQueryService.updateServiceDiscovery(namespace, form)` + `PUT /v1/service/patchService` + DTO；
- **验证**：
  1. 离线单测（mock `IServiceRuntimePort`）：成功路径的 ports 构造（`name` 小写拼接、`appProtocol`、`nodePort=null`、`targetPort`）、
     type 写死 `ClusterIP`、**Service 不存在 ⇒ 400 `k8s.call.api.failed`**、**工作负载不存在 ⇒ 400 `service.update.service.discovery.failed`**、
     patch 抛异常 ⇒ 400 `service.update.service.discovery.failed`；
  2. 在线**独立命名空间**探针：造一个 Deployment + Service，调 `patchService`（type=Service），
     核对 Service 的 `spec.type` 变成 `ClusterIP`、`spec.ports` 与容器端口一致，然后删命名空间（本会话标准做法）。
- **不做**：B 分支（规格已齐，下一轮实现）；A 分支里那句 `service为空` 的 500（**死代码**，不实现——我们映射成厂商的实际响应 400 `k8s.call.api.failed`）。

## 5. 实测结果（第 123 轮，真集群）

### 5.1 离线：`ServicePatchServiceTest` 7 例全绿（全量 **349** 条 0 失败）

覆盖：两端口成功路径（`name`/`appProtocol`/`port`/`targetPort`/`nodePort=null`）、**容器无端口时照样 patch 空 ports**（与 `/node_port` 不同）、
Service 不存在 ⇒ `k8s.call.api.failed`、工作负载不存在 ⇒ `service.update.service.discovery.failed`、
`K8sApiException` ⇒ `k8s.call.api.failed`、其它异常 ⇒ `service.update.service.discovery.failed`、`type != "Service"` ⇒ 明确报"尚未实现"。

### 5.2 在线：独立命名空间 `paas-verify-patchsvc`（造 Deployment + NodePort Service，验完删）

前置：Deployment `nps-svc` 容器端口 `8080/TCP`、`9090/UDP`；Service `nps-svc` 是 **`NodePort`**（k8s 分配的 `32264`/`31840`）。

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① 成功路径 | `{"type":"Service","serviceName":"nps-svc","portList":[{"containerPort":9999,…}]}` | `200 {"code":0,"data":true}`；Service 变成 **`ClusterIP`**；ports 变成 `tcp-nps-svc-0(8080/TCP,appProtocol=TCP)`、`udp-nps-svc-1(9090/UDP,appProtocol=UDP)`，**`nodePort` 被清掉**（merge-patch 里 `null` 生效）；**入参的 9999 完全没被使用**（照抄厂商：A 分支的 ports 来自容器） |
| ② Service 不存在 | `{"type":"Service","serviceName":"no-such-svc"}` | `400 调用API失败`（`k8s.call.api.failed`）——与 §2.1 的"404 走 ApiException"一致，**不是**那句死代码 500 |
| ③ Service 存在但工作负载不存在 | `{"type":"Service","serviceName":"ghost-svc"}` | `400 更新服务发现失败,请查看错误日志`（`service.update.service.discovery.failed`）——证实厂商内层 `SystemException` 被吞 |
| ④ `type != "Service"`（未实现分支） | `{"type":"ClusterIP","serviceName":"nps-svc"}` | `500 patchService 的 type != "Service" 分支尚未实现（见 docs/findings/118 第 3 节）`——**明确报错，不静默成功** |

⇒ **A 分支（服务发现的"端口/类型"写路径）已在真集群端到端验证**：k8s 侧的效果、三条错误映射、以及"入参不参与"这条怪行为都实测到了。

### 5.3 顺手修的一处分层漏洞

`ServiceRuntimePort.patchServicePorts` 原来**直接把 fabric8 异常漏给 domain**（同类里其它方法都翻译成 `K8sApiException`）。
本轮补上翻译（消息原样保留，所以 `updateServiceNodePort` 的 `msg.contains("422")` 判定不受影响）——
不补的话 A 分支根本没法区分"K8s 报错"与"其它异常"，而厂商对这两类用了**不同的 400 键**。

## 6. 遗留

- **B 分支**（`type != "Service"`）：规格已齐（§3），需要 5 样新东西（§3.1）——下一轮实现；
- 当前 B 分支返回 **500 + 明确文案**（不是 404 也不是假成功）；前端若在"端口映射"页保存会看到这条提示；
- `@AuditLog`（厂商两条分支上都有）——与其它端点同一笔账。

