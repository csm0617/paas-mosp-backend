# `GET /v1/service/{serviceName}` 勘查终局：**厂商实现在本环境自身就 500**（两个缺陷叠加）

**证据等级**：〔运行实测〕（临时改 label 拿到真实堆栈，已字节级还原）。2026-10-14。

## 1. 第一道门：`existService` 靠一个本环境不存在的 label

`TenxService.existService:2367-2391` 不是按名字查，而是按 **label selector** 查：
```
listNamespacedDeployment(ns, ..., "system/svcName=" + deployName, ...)
若为 0 再 listNamespacedStatefulSet(同 selector)
```
而 `gaop-namespace` 里**所有工作负载只有 `app=<name>` 标签**：
```
gaop-auth         {"app":"gaop-auth"}
gaop-be           {"app":"gaop-be"}
... （get deploy -l system/svcName → No resources found）
```
⇒ `isData()` 恒 false ⇒ 每个服务都被判"不存在"（这就是上一轮 8/8 全 400 的根因）。

**验证方式**：临时给 `gaop-diagnostic` 加上 `system/svcName=gaop-diagnostic`
（只动 Deployment 的 `metadata.labels`，**不动 selector / pod 模板 ⇒ 不触发滚动**，实测 Pod 没有重建）。
加完再打详情：**不再 400**了——第一道门确实就是它。

**还原**：`kubectl label deploy gaop-diagnostic system/svcName-`
→ labels 回到 `{"app":"gaop-diagnostic"}`，与留档**字节级一致** ✅

## 2. 第二道门（也是真正的终局）：厂商自己的代码 NPE

门过了以后，同一个请求变成 **HTTP 500 / `code=-1` / "系统错误"**，厂商返回的 trace 直指它自己的代码：

```
java.lang.NullPointerException
  at TenxService.responseContainer(TenxService.java:604)
  at TenxService.responseDeployment(TenxService.java:746)
  at TenxService.getServiceAndDeploymentByName(TenxService.java:2427)
  at TenxCloudImpl.service(TenxCloudImpl.java:387)
  at ServiceController.getServiceDetail(ServiceController.java:1641)
```

第 604 行是：
```java
if ((v1Resources = v1Container.getResources()).getRequests() != null && v1Resources.getLimits() != null) {
    BigDecimal cpuLimit = v1Resources.getLimits().get("cpu").getNumber();     // ← 这里
```
它只判断了 `getLimits() != null`，**没有判断 limits 里有没有 `cpu`/`memory` 键**；
本环境的工作负载没有完整的 `resources.limits`（或只有 requests）⇒ `get("cpu")` 返回 null ⇒ NPE。

**这是厂商的缺陷，不是数据缺失**：只要有"有 requests、没 limits（或 limits 缺 cpu/memory 键）"的容器，
这个端点就 500。

## 3. 终局结论

`GET /v1/service/{serviceName}` 在本环境**无法通过任何手段观测到成功响应**：

- 加 label 能过第一道门，但立刻撞上第二道门（厂商 NPE）；
- 除非去给厂商的工作负载**补上 resources.limits**（那是对厂商工作负载做实质性修改、会触发滚动，我不做）；
- 所以它与 `findings/50` 的结论一致但更硬：**不是"我们没找到办法"，而是"厂商这条路径本身是坏的"。**

## 4. 因此这个端点怎么办（写进下一轮的取舍）

三选一，都需要显式取舍、不能再拖：
1. **照代码实现 + 明确标注"未经对拉（厂商该端点在参考环境恒 500）"**——形状来源是 `/v1/service` 列表项
   （实测列表项与详情项是**同一个 `ServicesItem` 形状**）+ 处理器里那几处富化
   （`terminated`/`hostIP`/`imageInfo`/`nodePortService`/`nameUs`/`nameFr`）；
2. 用**独立临时 namespace** 造一个"资源 limits 齐全 + 带 `system/svcName` label"的 Deployment，
   让**我们的实现**能验证到形状（但仍然无法与厂商对比）；
3. 先跳过它，去做服务域那 30 个里**可对拉**的端点。

**我的倾向：先做 3，再做 1**——因为 1 的正确性完全依赖 `ServicesItem` 形状，而那个形状我们已经通过
`/v1/service` 列表**对拉验证过**（切片 1），所以 1 其实不是"没有证据"，而是"证据来自列表端点而非详情端点"。
把这个前提写清楚，1 就是可接受的。

## 5. 清理

临时 label 已移除并字节级核对；proxy 已关；未创建任何 K8s 对象；未写库。全量用例仍 **34 类 220 条全绿**。
