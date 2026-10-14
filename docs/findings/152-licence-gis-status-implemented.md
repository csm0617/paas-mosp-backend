# `GET /v1/licence/gisStatus` 规格 + 实现（第 159 轮）

**证据等级**：〔代码事实〕（厂商 `LicenceController:115-120` + `LicenceService.getGisActiveStatus:1151-1205` +
`application.yml:121-122` 逐行读）+〔运行实测〕（**四个分支全部实测**，含自建 stub 与真 Deployment 状态）。2026-10-14。

## 1. 端点

```java
@GetMapping("/gisStatus")
public Response<Integer> getGisActiveStatus()     // 0 未授权、1 已授权、2 无法请求到授权状态接口
```
配置：`gis.url`（厂商默认 **`${GIS_URL:gis-server:8080}`**）。

## 2. 流程

```
finlUrl = gis.url（没有 http(s):// 前缀就补 http://）
gisName = 去掉协议后、**冒号前**那一段            // 主机名
existGisService = false
try:
    deployment = 读 Deployment(gisName)
    status = deployment.status
    if (status != null):
        if (available!=null && ready!=null && replicas!=null && updated!=null)        ⇒ existGisService = true
        else if (ready!=null && replicas!=null && updated!=null && unavailable!=null)  ⇒ existGisService = true
        else ⇒ 日志"未正常运行"
catch (ApiException): 日志"未找到gis地图的服务存在"
if (existGisService):
    active = 1; existGis = false
    try:
        status = GET finlUrl + "/earthview/license/getLicenseStatus"
        active = Gson(status).EVServer；existGis = true
    catch: 日志
    if (existGis) return active
    return 2
return 2
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`gis.url` 的主机名被当作 Deployment 名** | 所以"GIS 是否在跑"是**用 K8s 状态判断的**，不是直接打接口 |
| 2 | **两组"字段全非空"条件** | 第一组含 `availableReplicas`、第二组含 `unavailableReplicas`（两者互斥地出现）；**都不满足 ⇒ 2 且不打接口** |
| 3 | **任何失败都收敛成 `2`** | Deployment 不存在 / 状态不满足 / HTTP 调用失败 / 解析失败 —— 全返回 2（实测②③） |

## 4. 实现与验证

**实现**：`ILicenceQueryService.queryGisActiveStatus(namespace)` + `LicenceController` 端点；
新增端口 `IGisLicenseStatusPort`（实现放 infrastructure，用 JDK `HttpClient` + Jackson 读 `EVServer`，
超时 10s，失败抛异常）；Deployment 状态复用已有的 `IServiceRuntimePort.readWorkloadState`
（**要求 `isDeployment()`**：我们的方法会回退 StatefulSet，而厂商只读 Deployment，所以回退命中时按"未找到"处理）；
`application.yml` 加 `gaop.gis.url: ${GIS_URL:gis-server:8080}`（与厂商同默认值、同环境变量名）。

### 4.1 离线：`LicenceGisStatusTest` 7 例，全量 **575** 条 0 失败

覆盖：Deployment 读不到 ⇒ 2 且**不打**接口；只命中 StatefulSet ⇒ 2；两组条件各触发一次接口调用；
状态不满足 ⇒ 2；接口抛异常 ⇒ 2；`https://host:port` 只取主机名。

### 4.2 在线：**四个分支全实测**

| 例 | 场景 | 实测 |
| --- | --- | --- |
| ① | **完整路径**：Deployment `127.0.0.1` 真的 1/1（用集群里已有的镜像起）+ 本机 18099 的 stub 返回 `{"EVServer":1}` | `200 data:1`；日志 `gis地图服务正常运行，进行请求地图机器码接口` |
| ② | 同上但**关掉 stub** | `200 data:2`；日志 `调用Gis地图获取授权状态出现异常…ConnectException` |
| ③ | **删掉 Deployment** | `200 data:2`；日志 `未找到gis地图的服务存在`（**没有**打接口） |
| ④ | Deployment 存在但 status 不满足两组条件 | `200 data:2`；日志 `gis地图服务未正常运行，不进行请求地图机器码接口` |

> ① 的构造值得记：`gis.url` 的主机名必须是**Deployment 名**，所以我把 `gis.url` 设成 `127.0.0.1:18099`
> 并真的建了一个名为 `127.0.0.1` 的 Deployment（K8s 名字允许点号），用集群里已有的镜像让它 1/1；
> stub 则监听本机 18099 —— 这样"Deployment 名"与"能解析到 stub 的主机名"同时成立。

### 4.3 收尾

探针 Deployment、stub 进程、临时 NodePort、后端进程全部回收（18099 端口已释放）；本轮不写库。
