# `GET /v1/service/gaop`：两个问题都定论了（一个修掉、一个是厂商依赖外部服务）

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14。

## 1. 问题 B（我们的 bug）——**已修并验证**

**根因**：上一轮那个批量补丁脚本在改 `ServiceRuntimePort` 的辅助方法时断言失败**中途退出**，
而它前面的改动已经落盘 ⇒ 装配器的 `fixedName` 那一处**从未被改**（留在 `.fixedName(s.getName())`）。
这是"能中途失败的补丁脚本"的典型危害：**半成品状态 + 看起来像成功了**。

**修复后实测**（`type=1`）：

```
gaop-auth          框架授权
gaop-be            框架后端
gaop-gateway-auth  网关
gaop-redis         框架redis
gaop-startup       框架分批启动控制
gaop-web           框架前端
```

**并且做了回归**：`/v1/service` 的 `fixedName` **仍然是服务名**（已对拉通过的行为一字未变）——
这类"为了新端点改装配器"的改动最容易顺手弄坏一个已验证端点，所以必须回归。

顺带拿到一条契约级证据：`keyword` 超 45 时我们返回 400
`"搜索服务名称不合法，字符长度应在0~45个字符以内"`——与厂商文案一致，
说明 `service.invalid.service.name.length` 这个键在我们既有的 i18n 包里**已经是正确的那条**。

## 2. 问题 A（厂商为什么空）——**已定论：它依赖一个本环境不存在的外部服务**

`allPageService` 是 `TenxCloudSingle` 的 default 方法（`third/TenxCloudSingle.java:25-37`），它调用：

```java
@RequestLine("GET /api/v2/clusters/{clusterId}/services")
TenxResponse<ServiceList> allService(@Param("clusterId") String id, @QueryMap Page page);
```

即 **一次对 TenX 云 API 的远程 HTTP 调用**（分页 100 条一页）。
本环境没有该外部服务 ⇒ 返回空 ⇒ `gaop` 端点**恒为空列表**。

**这与 `/v1/service` 有本质区别**：`/v1/service` 直接从 K8s 读（所以本环境有 9 个服务），
而 `gaop` 走 TenX 云。这也解释了为什么"列表有数据、gaop 却没有"。

## 3. 因此这个端点的定位

它与 `GET /{serviceName}`（`findings/51`，厂商自身 NPE）、`existService`（`findings/51`，缺标签恒 false）
属于**同一类：厂商在该参考环境里的这条路径不可用**。但有一处重要区别：

> **我们的实现是按 K8s 直读 + 照抄过滤算法写的。在真实 MSOP 部署里（TenX 存在、工作负载带
> `system/appType` 标签），厂商会返回与我们相同的集合。** 参考环境里的分歧**完全来自缺失的 TenX**，
> 不是算法差异。

所以处置是：**保留实现 + 记录成因**，而不是为了"在本环境看起来一致"去模仿"返回空列表"——
那样做等于把一个可用的功能改成一个永远空壳，只为迎合一个坏掉的参考。

过滤算法本身已有 5 条离线单测（含照抄的缺陷），是这段逻辑的独立保证。

## 4. 仍未做的一条（如实）

厂商 keyword 的**兜底**匹配 `ServiceController.filterServiceName` 还没读全，
目前实现是"名字或显示名忽略大小写包含"的**近似**（代码注释已标注）。
按 §3 的结论，这条近似不影响本环境的可观测结果，但**在真实部署里会影响 keyword 命中的集合**，
所以仍列在待办里。

## 5. 状态

全量 **34 类 225 条全绿**；本轮实测只读 + 回归；临时 NodePort 已删、无残留进程、工作树干净。
