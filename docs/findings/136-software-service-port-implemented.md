# `POST /v1/software/servicePort` 规格 + 实现（第 143 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:502-507` + `SoftwareService.softwareServicePort:2836-2876` 逐行读）
+〔运行实测〕（真库）。2026-10-14。

## 1. 端点

```java
@PostMapping("/servicePort")
public Response<Boolean> updateServicePort(@RequestBody List<ServicePortForm> form)
```
`ServicePortForm{containerPort, serviceName}`——注意字段名是 **containerPort**，写进 `g_service_port.port` 的就是它。

## 2. 流程

```
currentTimeMillis = now（**一次调用只取一次**，所有行共用）
portList = form.map(getContainerPort)
serviceName = form.get(0).getServiceName()          // 空列表 ⇒ IndexOutOfBounds ⇒ 500（照抄不兜）
appList = g_software_app where app_name = serviceName
if (appList 为空) ⇒ **直接 return true**（不做事、也不通知网关）
for softwareId in appList.map(getSoftwareId):
    旧入口 = g_service_port where software_id=? and service_name=? and web_entry=true 的**第一行**
    isEntry / p = 旧入口存在 且 **它的 port 还在 portList 里** ? (true, 那个 port) : (false, 0)
    **delete** g_service_port where software_id=? and service_name=?
    for port in portList:
        insert {softwareId, serviceName, port,
                web_entry = (isEntry && port == p),
                path_name = **serviceName + "-" + port**,
                updated_at = currentTimeMillis}        // **不写 `allow`**
notificationGateway()                                       // 通知网关刷新（fire-and-forget）
return true
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **web 入口标记的"继承"规则** | 只有当**旧入口那一行的 port 仍在新列表里**时，新行才继续标 `web_entry=true`（且只有那一个 port）；旧入口消失则全 false |
| 2 | `path_name = serviceName + "-" + port` | 与 `patchService` B 分支的 `deploymentName + "-" + port` 同一拼法 |
| 3 | **`updated_at` 一次调用只取一次** | 循环外取 `System.currentTimeMillis()`，所有行共用同一时间戳 |
| 4 | `allow` **不写** | 实测无差别：`g_service_port.allow` 是 `NOT NULL DEFAULT 1`，所以"不写"与 `patchService` 那处显式写 `true` 结果都是 1（**这个差异不可观测**） |

另外两处：`serviceName` 只取**第一条**（同一个 form 里若混了多个服务名，其余的会被忽略）；
同一个 `app_name` 关联的**所有**软件都会写。

## 4. 实现与验证

**实现**：`IServiceQueryService.updateSoftwareServicePorts` + 控制器端点 + `ServicePortItemVO`/`ServicePortFormDTO`；
新增 DAO/仓储 `selectWebEntryPort` / `insertServicePortRow`（**不写 allow**）；**复用**已有的
`querySoftwareIdsByAppName`、`deleteServicePorts`、以及 `patchService` 那轮建的 `IGatewayRefreshPort`。

### 4.1 离线：`SoftwareServicePortTest` 7 例，全量 **470** 条 0 失败

覆盖：web 入口 port 仍在 ⇒ 只有它 true；旧入口消失 ⇒ 全 false；原本没有入口 ⇒ 全 false；
同一 `app_name` 下多个软件都写；未知 serviceName ⇒ 不碰库且不通知网关（返回 true）；
空 form ⇒ `IndexOutOfBoundsException`（照抄厂商）；**先删后插**的顺序。

### 4.2 在线：真库（seed `g_software{sw-sp}` + `g_software_app{sw-sp, svc-sp}` + 旧行 `{port=8080, web_entry=true, allow=0}`）

| 例 | 请求 | 实测（`SELECT port,path_name,web_entry,allow`） |
| --- | --- | --- |
| ① | `[{svc-sp,8080},{svc-sp,9090}]` | `200 data:true`；**两行**：`8080 / svc-sp-8080 / web_entry=1`、`9090 / svc-sp-9090 / web_entry=0` ⇒ 旧入口的 port 仍在 ⇒ 继续标 true |
| ② | `[{svc-sp,9090}]` | `200 data:true`；**只剩一行** `9090 / svc-sp-9090 / web_entry=0` ⇒ 旧入口消失 ⇒ 标记丢掉 |
| ③ | `[{no-such-svc,1234}]` | `200 data:true`；该软件行数**仍是 1** ⇒ 未知 serviceName 不碰库 |

（`allow` 实测都是 1——与第 3 节第 4 点的列默认值一致；seed 时故意写的 `allow=0` 在重插后变回 1。）

### 4.3 收尾

探针 `g_service_port`/`g_software_app`/`g_software` 行已删（三者都回到 0）、临时 NodePort 与后端进程已回收。
