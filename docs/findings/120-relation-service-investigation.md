# `GET /v1/software/relationService/{id}` 调查（第 125 轮）——**第 127 轮更正：原"依赖 TenX 云 API"的结论是错的**

**证据等级**：〔代码事实〕（厂商 `SoftwareController:430-435` + `SoftwareService.softwareRelationServiceList:3064-3158` +
`getServiceMappingInfo:2974-3048` + **`TenxCloudImpl` 实现类逐行读**）。2026-10-14。

> ## ⚠️ 先看更正
>
> **第 125 轮本文的结论"`mappingInfo` 只有 TenX 云能算出来 ⇒ 建议降级"是错的。**
> 错因：我只看了**接口名** `this.tenxCloud.*`，没有去看**唯一实现类**。
>
> **事实（第 127 轮逐行核实）**：`TenxCloud` 的唯一实现是 `controller/cloud/TenxCloudImpl.java`（`@Component`），
> 它是**本地 K8s 适配层**，不是远程服务：
>
> | 调用 | 实现 | 证据 |
> | --- | --- | --- |
> | `tenxCloud.clusters()` | **硬编码** `CID-4f081ba297ba`，无网络调用 | `TenxCloudImpl:128-137` |
> | `tenxCloud.existService(id, name)` | → `tenxService.existService(name)` → **`listNamespacedDeployment`**（标签 `system/svcName=<name>`） | `TenxCloudImpl:351-353`、`TenxService:2367-2372` |
> | `tenxCloud.service(id, name)` | → `tenxService.getServiceAndDeploymentByName(name)`（K8s 读） | `TenxCloudImpl:312-318` |
> | `tenxCloud.existApp` / `createApp` / `getDeploymentYaml` | 全部 → `tenxService.*`（`TenxService` 里有 **54 处** `k8sApiClientManager` 调用） | `TenxCloudImpl:262-274,486-489` |
> | 全文件**唯一**的真实 HTTP | **Loki** 日志查询 `/loki/api/v1/query_range`（只被 `getHistoryLog` 用 ⇒ `/v1/service/logs`） | `TenxCloudImpl:181` |
>
> 还有一处更直接的证据：`getServiceMappingInfo` 里那段 `ingressInfo` 读的是
> `servicesItem.getService().getProxy()`，而 `IoK8sApiCoreV1Service.proxy` 是**厂商自建 DTO 上的一个字段**
> （`third/k8s/IoK8sApiCoreV1Service.java:23` 的 `@JsonProperty("proxy")`），
> **全仓库没有任何一处 `setProxy` 调用** ⇒ `getProxy()` **恒为 null** ⇒ `ingressInfo` **恒为空列表**。
> 原生 K8s 的 Service JSON 里也没有 `proxy` 键，所以反序列化也填不上。
>
> ⇒ **`relationService` 的 `mappingInfo` 完全可以用本地 K8s 复现**（`intranetInfo`/`nodePortInfo` 来自
> Service 与 `<name>-nodeport` 的 ports，`ingressInfo` 恒空）。本文**不再是"降级"的依据**。
>
> **方法教训（写进 memory）**：判断外部依赖必须看**实现类**；接口名（`tenxApi`/`tenxCloud`）会骗人。
> 本会话此前也栽过一次同类（把 `K8sImageServiceImpl` 当成生效实现，实际生效的是 `K3sImageServiceImpl`）。

## 1. 端点与流程

`GET /v1/software/relationService/{id}` → `softwareRelationServiceList(id)` → `Response.ok(List<ServicesItem>)`
（**与 `GET /v1/service` 的 `data.services[]` 同一个类型** ⇒ 已有的 `ServiceItemDTO`/`ServiceListAssembler` 可复用）。

```
1. software = selectByPrimaryKey(id)；不存在 ⇒ 400 `software.get.software.service.info.failed`
2. softwareApps = 该软件的 g_software_app 行（app_name 即"服务名"）
3. services = tenxApi.allPageService(null)            // 本地 K8s：列 Deployment（跨命名空间？见下）
   serviceMap = { deployment.metadata.name → ServicesItem }
4. items = listNamespacedPod(K8s.NAMESPACE)（失败只记日志）
5. 对每个 softwareApp：
     servicesItem = serviceMap.get(appName)          ← **匹配不到就是 null，随后立刻 NPE**
     mappingInfo = getServiceMappingInfo(appName)    // 本地 K8s，见第 2 节
     servicesItem.setMappingInfo(...)
     restartCount = 同名 pod 的第一个 containerStatus.restartCount
     type = deployment 注解 gaop/serviceType
     serviceAuth = g_service_auth 按 service_id 查
         null ⇒ type == "前台服务" ? 造 {auth:AUTHORIZED, authType:1} : null
         非 null 且已过期 ⇒ 改成 EXPIRED + **写库**（读接口带副作用）
     把 deployment 注解 gaop/serviceLabel 换成 i18n 自动名（只改响应里的快照，不写集群）
6. 列表非空则返回，否则返回空列表
```

## 2. `getServiceMappingInfo(serviceName)` 的**真实**依赖（全是本地 K8s）

```java
String clusterID = tenxCloud.clusters()...getClusterID();          // 硬编码字符串
ExistService existService = tenxCloud.existService(clusterID, serviceName);
if (!existService.isData()) { log; return null; }                  // 没有该 Deployment ⇒ 返回 null
TenxResponse<Map<String, ServicesItem>> service = tenxCloud.service(clusterID, serviceName);   // K8s 读
ServicesItem servicesItem = service.getData().get(serviceName);
if (servicesItem.getService() == null) throw new WrongArgumentException("service.get.service.info.error");
// intranetInfo：Service 的每个 port ⇒ {containerPort: port.port, intranetPort: "<serviceName>:<port>"}
// nodePortInfo：上面那些 port 里 nodePort 非空的 + <serviceName>-nodeport 这个 Service 的全部 port
// ingressInfo：servicesItem.getService().getProxy() —— **恒 null** ⇒ 恒空列表
```

⇒ 我们已有 `IServiceRuntimePort` 的 Service 读能力（`serviceExists`、`patchServicePorts` 等），
补一个"读 Service 的 ports + 读 `<name>-nodeport`"就能算出 `intranetInfo`/`nodePortInfo`；
`ingressInfo` 恒 `[]`（与 `/v1/service` 列表端点恒返回三键全 null 的 `mappingInfo` **不同**：这里三键是**数组**）。

## 3. 仍然存在的**真问题**：匹配不到就 NPE

`serviceMap.get(appName)` 的结果**没有判空**，紧接着 `servicesItem.setMappingInfo(...)`。
⇒ 只要 `g_software_app.app_name` 里有一个名字在 k8s 服务列表里不存在（**删了服务但没清关联**，本环境常见），
厂商就 **NPE ⇒ 500**。这是厂商缺陷：实现时要么照抄 500，要么跳过该行（**需要先决定并记录**）。

## 4. 修订后的建议

| 选项 | 说明 |
| --- | --- |
| **A. 实现（推荐）** | 依赖全部是本地 K8s + 我们的库；`mappingInfo` 三键按第 2 节算（`ingressInfo` 恒空）。**可离线单测 + 真集群验证** |
| B. 降级 | ~~依赖 TenX 无对端~~ ⇒ **理由已不成立**，不再作为选项 |

**实现前置问题（三个，都已查清）**：
1. **`tenxApi.allPageService(null)` 对应什么** ⇒ `TenxCloudImpl.allPageService:692-707` 是"把 `allService(clusterID, page)`
   按 100 分页全取一遍"（`clusters()` 是硬编码 ID），而 `allService` 的数据源与我们的
   `IServiceRuntimePort.queryServices(namespace)` **是同一个**（`GET /v1/service` 已与厂商逐字节对拉通过）。
   ⇒ **直接用 `queryServices(namespace)`**，不要再造一个"跨命名空间"的读法；
2. **`i18nUtil.getAutoNameMessageByCurrent("service", key, name)` 的等价物**：`g_setting` 的
   `i18n.service.<lang>.<key>`；`ServiceQueryServiceImpl.updateServiceName:64-65` 已经在写这个键，
   读侧用 `ISettingRepository` 取值即可（本环境无覆盖数据 ⇒ 取不到就用原 name，与厂商一致）；
3. **`g_service_auth` 过期写回**要照抄（读接口带副作用）：需要给 `IServiceAuthRepository` 加
   "按 service_id 查"与"改 auth=EXPIRED/updated_at"两个方法（现在只有 `queryAll()`）。

## 5. 状态

第 125 轮：只做了调查、无代码改动。
**第 127 轮：更正本文结论**（无代码改动，只修文档）；全量 **374** 条用例全绿，工作树干净。
