# `GET /v1/software/relationService/{id}` 调查：**依赖 TenX 云 API，建议降级**（第 125 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:430-435` + `SoftwareService.softwareRelationServiceList:3064-3158` + `getServiceMappingInfo:2974-3048` 逐行读）。2026-10-14。**本轮不实现，先记录判断依据。**

## 1. 端点与流程

`GET /v1/software/relationService/{id}` → `softwareRelationServiceList(id)` → `Response.ok(List<ServicesItem>)`
（**与 `GET /v1/service` 的 `data.services[]` 是同一个类型**，所以我们已有的 `ServiceItemDTO`/`ServiceListAssembler` 可复用）。

```
1. software = selectByPrimaryKey(id)；不存在 ⇒ 400 `software.get.software.service.info.failed`
2. softwareApps = 该软件的 g_software_app 行（app_name 即"服务名"）
3. services = tenxApi.allPageService(null)            // k8s 侧的服务列表
   serviceMap = { deployment.metadata.name → ServicesItem }
4. items = listNamespacedPod(K8s.NAMESPACE)（失败只记日志）
5. 对每个 softwareApp：
     servicesItem = serviceMap.get(appName)          ← **匹配不到就是 null**
     mappingInfo = getServiceMappingInfo(appName)    ← **见第 2 节：TenX 云 API**
     servicesItem.setMappingInfo(...)                ← null 时这里 NPE
     restartCount = 同名 pod 的第一个 containerStatus.restartCount
     type = deployment 注解 gaop/serviceType
     serviceAuth = g_service_auth 按 service_id 查
         null ⇒ type == "前台服务" ? 造 {auth:AUTHORIZED, authType:1} : null
         非 null 且已过期 ⇒ 改成 EXPIRED + **写库**（读接口带副作用）
     把 deployment 注解 gaop/serviceLabel 换成 i18n 自动名（只改响应里的快照，不写集群）
6. 列表非空则返回，否则返回空列表
```

## 2. 为什么建议降级：`mappingInfo` 只有 TenX 能算出来

`getServiceMappingInfo(serviceName)`（`:2974-3048`）的第一件事就是：

```java
String clusterID = this.tenxCloud.clusters().getClusters().get(0).getClusterID();
ExistService existService = this.tenxCloud.existService(clusterID, serviceName);
if (!existService.isData()) { log; return null; }                       // 服务"不存在"⇒ 返回 null
TenxResponse<Map<String, ServicesItem>> service = this.tenxCloud.service(clusterID, serviceName);
...
if ((proxy = servicesItem.getService().getProxy()) != null && ...) {    // ← 这个 proxy 字段是 TenX 包装出来的
    ingressInfo.setContainerPort(serviceProxy.getInternalPort());
    ingressInfo.setIngressPort(serviceProxy.getLoadbalanceIP() + ":" + serviceProxy.getExternalPort());
}
```

- `intranetInfo`/`nodePortInfo` 可以从原生 K8s 对象算出来（Service 的 ports 与 `<name>-nodeport`）；
- **`ingressInfo` 依赖 `IoK8sApiCoreV1ServiceProxy`**——那是**时速云（TenX）包装对象**里的字段，原生 K8s 没有；
- 更关键的是**进入这个函数本身**要先过 `tenxCloud.existService(...)`：没有 TenX 对端时，这一步要么抛异常（⇒ 厂商 500）、
  要么返回 `isData()==false`（⇒ 返回 null ⇒ `mappingInfo` 变成三键全 null 的空对象）。

⇒ 这属于本仓库已记录的 **C3 类**（"TenX 侧效应：无 TenX 对端 ⇒ 未实现，记录为待办"）。
**在参考环境里我们无法确定厂商的实际输出是哪一种**（500 还是三键全 null），而厂商 pod 目前 Metaspace 耗尽、
无法实测——**没有实测就不该猜**（本会话已经吃过三次"猜对端行为"的亏）。

## 3. 第二个风险：匹配不到就 NPE

`serviceMap.get(appName)` 的结果**没有判空**，紧接着 `servicesItem.setMappingInfo(...)`。
⇒ 只要 `g_software_app.app_name` 里有一个名字在 k8s 服务列表里不存在（**删了服务但没清关联**，这在本环境很常见），
厂商就 **NPE ⇒ 500**。这是厂商缺陷，不是我们该照抄的行为；真要实现，得先决定"照抄 500"还是"跳过该行"。

## 4. 结论与建议

| 选项 | 说明 |
| --- | --- |
| **A. 降级（建议）** | 与 `serviceInfoByNode` 同处理：**不实现**，在偏离表里记为"依赖 TenX 云 API / 无对端"，等有 TenX 或明确需求时再做 |
| B. 只做不依赖 TenX 的部分 | `intranetInfo`/`nodePortInfo` + restartCount + serviceAuth（含 EXPIRED 写回）+ i18n 自动名；`mappingInfo.ingressInfo` 恒空、`existService` 判空跳过 |
| C. 照抄 | 需要 TenX；且要故意复现 NPE ⇒ 不可取 |

**若将来做 B**，请先确认三件事：
1. `tenxApi.allPageService(null)` 在我们的实现里对应 `IServiceRuntimePort.queryServices(namespace)` 还是"跨命名空间"；
2. `i18nUtil.getAutoNameMessageByCurrent("service", key, name)` 的等价物（`g_setting` 里 `i18n.service.<lang>.<key>`，见 `updateServiceName` 的实现）；
3. `g_service_auth` 过期写回的那次 `updateByPrimaryKeySelective` 要照抄（读接口带副作用，与 `findings/88` 的 `updateServiceNodePortsEndpoint` 同类）。

## 5. 状态

本轮**没有代码改动**（只做了这次调查与结论）；工作树干净；全量 **368** 条用例全绿。
