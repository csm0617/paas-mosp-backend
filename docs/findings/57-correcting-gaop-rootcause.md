# 更正 `findings/55`：我给 `gaop` 空列表的根因**是错的**

**证据等级**：〔运行实测〕。2026-10-14。本轮**只做更正与勘查**，未改业务代码。

## 1. 上一轮我写了什么

`findings/55` §2 的结论是：

> `allPageService` 是 `@RequestLine("GET /api/v2/clusters/{id}/services")`（对 TenX 云 API 的远程调用），
> 本环境没有该外部服务 ⇒ 返回空 ⇒ **`gaop` 端点恒为空列表**。

## 2. 新证据把它否掉了

本轮探测 `GET /v1/service/serviceAuth`（它**用的是同一个数据源** `tenxCloud.allPageService(null)`，见
`ServiceController:3495`）：

```
{"runningDeployment":0,"total":9,"count":9,"services":[ ... 9 个带完整 deployment 的服务 ... ]}
```

**同一个数据源返回了 9 条。** 所以"`allPageService` 在本环境返回空"这个前提**不成立**，
由它推出的 `gaop` 空列表根因也**不成立**。

（`TenxCloudImpl` 很可能**覆盖**了接口上的 `default allPageService` 而走本地 K8s——我没有去确认，
因为结论已经不需要它了：**同源不同结果，说明差异在 `gaop` 自己的过滤链上，不在数据源**。）

## 3. 顺带排除了另一个猜测

我原本怀疑 `gaop` 空是因为工作负载**有 `system/appType` 标签**、而值不是 `gaop-core`/`gaop-base`
（那样确实会一个都不匹配）。实测：

```
gaop-be/gaop-auth/... : labels = {"app":"gaop-be"}   ← 没有任何 system/* 标签
```

即 `appType == null`，按代码应走 `serviceInPassFrame(name)`，而 `gaop-be` **在**那份 9 个名字的框架名单里
⇒ **按代码它应该被保留**。所以这个猜测也被排除。

## 4. 那么真因是什么？——**仍未定论**

现有证据只支持一件事：**`gaop` 的过滤链在参考环境里把 9 条全丢掉了**，而同一个数据源在
`serviceAuth` 里是好的。嫌疑集中在 `gaop` 自己那段（`ServiceController:1563-1660`）：
- 过滤 lambda 里 `if (result) { ... }` 块很长，**我还没逐行读完**（其中夹着 gis-server/gis-jb 的追加逻辑）；
- 结尾的 `pagination(collect, page, size)` 与 keyword 两段式的交互；
- `type` 是必填参数，若 handler 里还有一处对 `type` 的隐式依赖（比如空值判断顺序），也可能整体落空。

**在读完那一段之前，`gaop` 的对拉仍然无效**——但这次我把结论写成"未定论"，而不是再猜一个根因。

## 5. 本轮顺带确认的两件事

1. **`serviceAuth` 是可观测的**（9 条、带完整 `deployment`），下一个可做的端点就是它；
   它的过滤规则第一段也已读到：保留 `system/appName` **既不是 `"gaop"` 也不是 `"gis-server"`** 的服务
   （本环境该标签缺失 ⇒ 9 条全保留，与实测一致）。**但 handler 后半段（授权状态过滤、分页、装配）还没读完。**
2. **`baseComponent` 可观测但内容退化**：返回 2 条，每条 `{daemonSet: null, v1PodList: null}`
   —— 本环境 `kube-system` 里没有它要找的那些 DaemonSet。它能对拉，但参考价值很低。

## 6. 教训（这次是"推论"层面的）

`findings/55` 的错法比之前几次更隐蔽：我不是抄错了值、也不是只验了形状，而是**从一个正确的前提
（那段代码确实在调远端）推出了一个错误的结论（所以结果必为空）**——**漏掉了"该方法可能被覆盖/该前提在本环境不一定成立"这一步验证**。

可复用的自查：**凡是"X 所以 Y 必然为 Z"的推论，如果 Y 的结果本身是关键结论，就必须先用一个独立观测把它证实**。
本轮那个独立观测（`serviceAuth` 同源却有 9 条）只是一条 curl 的成本。

## 7. 状态

全量用例仍 **34 类 228 条全绿**；本轮零业务代码改动；一次只读探测；无残留资源。
