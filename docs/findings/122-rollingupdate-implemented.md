# `PUT /v1/service/rollingUpdate` 规格 + 实现（第 129 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.rollingUpdate:2599-2644` + `setRollingUpdate:2646-2654` +
`SoftwareService.updateSoftwareTimeByService:3776-3788` + `SoftwareMapper.getSoftwareIdList` 逐行读）
+〔运行实测〕（真集群独立命名空间 + 真库）。2026-10-14。

## 1. 端点与入参

```java
@PutMapping("/rollingUpdate")
public Response<Boolean> rollingUpdate(@RequestBody ServiceImageForm form)   // {name, type, version}
```

⇒ 这是前端「更新镜像 / 滚动更新」按钮走的那个端点（`umi.d08aa716.js` 里确有 `/v1/service/rollingUpdate`）。

## 2. 流程

```
1. 读 Deployment(form.name)：ApiException ⇒ 400 `service.not.found.update.image.version`（**带服务名**）
2. type == 1 ⇒ setRollingUpdate(deployment, maxSurge=1, maxUnavailable=0)
   type == 2 ⇒ setRollingUpdate(deployment, maxSurge=0, maxUnavailable=1)
   其它 type  ⇒ **不改策略**
   （setRollingUpdate：strategy.type = "RollingUpdate"，并**整体换掉** rollingUpdate 对象）
3. 注解 `gaop/updateTimestamp` = 当前毫秒
4. `spec.minReadySeconds = 2`
5. `containers[0].image = form.version`（**只改第一个容器**）
6. 整份 merge-patch Deployment
7. **patch 成功之后**才 `updateSoftwareTimeByService(form.name)`：
     按 `g_software_app.app_name = form.name` 找软件 id（`getSoftwareIdList`），逐个 `updated_at = now`
8. 成功 ⇒ 200 `{code:0,data:true}`；patch 的 ApiException ⇒ 400 `k8s.call.api.failed.to.change.image.version`
```

## 3. 四处要照抄的点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`type` 只认 1/2** | 其它值（含 null）**不改策略**，但**照样改镜像**——不是"非法参数直接报错" |
| 2 | 策略值是**整型形态** | `IntOrString(1)`/`IntOrString(0)` ⇒ JSON 里是 `{"maxSurge":1,"maxUnavailable":0}`，**不是** `"25%"`；而 `strategy.rollingUpdate` 被**整体替换**（原有字段不保留） |
| 3 | **只改 `containers[0]`** | 与 `healthCheck`、`updateVolumes` 同款"只看第一个容器" |
| 4 | **先 patch 后写库** | patch 失败时**不动** `g_software.updated_at`（顺序可观测：失败响应下库里时间不变） |

另外：`minReadySeconds` 恒写 2；注解 `gaop/updateTimestamp` 是**毫秒字符串**。

## 4. 两处边界（都照抄厂商的"不兜"）

1. `containers.get(0)`：容器列表为空 ⇒ `IndexOutOfBoundsException` ⇒ **500 `系统错误`/`code=-1`**（厂商没兜）；
2. `strategy` 为 null 时 `setRollingUpdate` 会 NPE（`type=1/2` 才走），但 API Server 会给 Deployment 默认
   `strategy`，所以实际不可达。

## 5. 落地与验证

**实现**：`IServiceQueryService.updateServiceImageRollingUpdate` + `IServiceRuntimePort.patchDeploymentRollingUpdate`
（新端口）+ `PUT /v1/service/rollingUpdate` + `ServiceImageDTO/VO`；
仓储新增 `updateSoftwareUpdatedAt`（**不能**用现成的 `updateByPrimaryKey`——那条 SQL 刻意不写 `updated_at`，见 `ISoftwareDao`）。

### 5.1 离线：`ServiceRollingUpdateTest` 5 例，全量 **388** 条 0 失败

覆盖：type=1 透传 + 两个关联软件各写一次 `updated_at`；type=99 照样 patch；
Deployment 不存在 ⇒ 400（带服务名）且**不写库**；patch 失败 ⇒ 400 `k8s.call.api.failed.to.change.image.version`
且**连查库都不查**；容器为空 ⇒ `IndexOutOfBoundsException` 原样冒出。

### 5.2 在线：独立命名空间 `paas-verify-roll` + 真库（造 `g_software`/`g_software_app`，验完删）

前置：Deployment `nps-svc`（默认策略 `25%/25%`、`minReadySeconds` 未设、镜像 `nginx:alpine`）；
`g_software{sw-roll, updated_at=222}` + `g_software_app{app_name=nps-svc}`。

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `{type:1, version:"nginx:1.26"}` | `200 data:true`；`strategy={"maxSurge":1,"maxUnavailable":0}`（**整型**）、`type=RollingUpdate`、`minReadySeconds=2`、`image=nginx:1.26`、注解 `gaop/updateTimestamp` 已写；**库里 `updated_at` 从 222 变成当前毫秒** |
| ② | `{type:2, version:"nginx:1.27"}` | `200`；`strategy={"maxSurge":0,"maxUnavailable":1}`；镜像已换 |
| ③ | `{type:99, version:"nginx:1.28"}` | `200`；**策略保持 ② 的 `0/1` 不变**，但**镜像换成了 1.28** ⇒ "其它 type 不改策略但仍改镜像"实测确认 |
| ④ | `{name:"no-such", type:1}` | `400 未找到该服务no-such, 更新镜像版本失败` |

### 5.3 收尾（含环境复现）

实验室数据已还原（`g_software`=0、`g_software_app`=0、`g_user`=1）；命名空间、临时 NodePort、后端进程全部回收。

**环境复现**：`kubectl delete namespace` 又一次卡在 `Terminating`——原因与 `findings/120` §6.5 记录的一致
（集群 **Calico 聚合 API discovery 陈旧** `projectcalico.org/v3: stale GroupVersion discovery`），
内容确认清空后清 finalizer 完成删除。**这不是我们造成的，但每次删验证命名空间都会遇到**；
下次可以直接 `--wait=false` + 清 finalizer，省掉等待。
