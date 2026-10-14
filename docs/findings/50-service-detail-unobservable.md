# `GET /v1/service/{serviceName}`：**厂商在本环境根本返回不出成功响应**——先别做

**证据等级**：〔运行实测〕（8 个服务全 400）+〔代码事实〕+〔前端事实〕。2026-10-14。
本轮**没有写业务代码**：勘查的结论是"现在做它是错的"，值得单独记一篇。

## 1. 实测：列表里有的服务，详情一律"不存在"

拿 `/v1/service` 列表返回的**前 8 个服务名**，逐个打详情：

```
gaop-auth            400  服务gaop-auth不存在
gaop-be              400  服务gaop-be不存在
gaop-diagnostic      400  服务gaop-diagnostic不存在
gaop-fdfs            400  服务gaop-fdfs不存在
gaop-gateway-auth    400  服务gaop-gateway-auth不存在
gaop-image-uploader  400  服务gaop-image-uploader不存在
gaop-startup         400  服务gaop-startup不存在
gaop-redis           400  服务gaop-redis不存在
```

**8/8 全部失败**（`service.service.name.not.found`，HTTP 400）。

## 2. 根因：存在性由 TenX 层判定，而它在本环境恒返回 false

`ServiceController.getServiceDetail`（`:1405-1409`）第一步就：
```java
String clusterID = tenxCloud.clusters().getClusters().get(0).getClusterID();
ExistService existService = tenxCloud.existService(clusterID, serviceName);
if (!existService.isData()) throw new WrongArgumentException("service.service.name.not.found", serviceName);
```
而 `TenxCloudImpl.existService:351-352` 又直接转给 `tenxService.existService(serviceName)`。
本环境这一层没有真实对端 ⇒ `isData()` 为 false ⇒ **每个服务都被判"不存在"**，
哪怕它明明出现在同一个后端返回的列表里。

## 3. 这个结论的直接后果：**这个端点现在无法做出可验证的实现**

前几轮能做成，靠的都是"对拉厂商"：拿到成功响应 → 逐字段比。而这个端点**拿不到成功响应**，
于是：
- 规模上它是 19 键的 `ServicesItem`，且 `service`/`deployment` 里嵌的是**整个 K8s 对象**；
- 若照代码写完，我只能验证"形状像我读到的代码"，**没有任何外部证据**证明它与厂商一致；
- 而"注释写对了、代码写错了"这件事我这几轮已经栽过两次（空白 desc、`edit()` vs patch）。

**所以本轮的结论是：先别做，先解决"怎么验证"。**

## 4. 前端确实在用它（所以不能永久放弃）

前端 `umi.d08aa716.js` 里有三处 `"/v1/service/".concat(...)`（分别拼 `service`/`serviceName`），
说明服务详情是真实被调用的。**这个端点必须做，只是"先想清楚怎么验证"。**

## 5. 服务域的真实全貌（本轮从**前端**侧拿到的清单，共 33 个）

这比从 controller 里数更准——**前端真正会调的**才是契约面：

```
/v1/service                        /v1/service/labels          /v1/service/patch
/v1/service/{name}                 /v1/service/node            /v1/service/patchService
/v1/service/gaop                   /v1/service/hostName        /v1/service/patchCommand/{name}
/v1/service/serviceInfoByNode      /v1/service/event           /v1/service/updateName
/v1/service/servicePods            /v1/service/restartInfo     /v1/service/updateServiceRole
/v1/service/serviceAuth            /v1/service/healthCheck     /v1/service/scale
/v1/service/baseComponent          /v1/service/imageStorage    /v1/service/rollingUpdate
/v1/service/baseEvent              /v1/service/logs            /v1/service/schedulingNode
/v1/service/otherPlugin            /v1/service/priority        /v1/service/startUp
/v1/service/tolerance              /v1/service/restartInfo     /v1/service/cancelStartUp
/v1/service/reStartBaseService     /v1/service/batch-start     /v1/service/batch-stop
/v1/service/batch-restart
```

已完成 3 个（`/v1/service`、`/services`→…、`labels`、`updateName`——即 `GET /v1/service`、`labels`、`updateName`），
**剩 30 个。**

## 6. 下一轮的建议（先解决"怎么验证"，再动手）

1. **查 `tenxService.existService` 到底看什么**——如果它只是查某个空表/空目录，那么造一条最小数据
   就能让厂商返回成功响应，从而**恢复可对拉**（这是最有价值的下一步，成本可能很低）；
2. 若确实无法恢复，则改为"**按 list 项形状 + 处理器里那几处富化**（`terminated`/`hostIP`/`imageInfo`/`nodePortService`/`nameUs`/`nameFr`）实现，
   并在文档里明确标注**未经对拉**"，同时用独立临时 namespace 造一个服务做形状验证；
3. 无论走哪条，都不要再出现"写完了但没有任何外部证据"的端点。

## 7. 状态

本轮零业务代码改动；集群只做只读探测；全量用例仍 **34 类 220 条全绿**；无临时资源残留。
