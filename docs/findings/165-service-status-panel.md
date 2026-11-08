# 165 · 概览页状态面板的接口、键名与两个标签缺失

**轮次**：2026-11-08 第 4 轮（承接 164 的端点补全）
**触发**：用户在集群页面上截图「系统状态 / 基础服务状态」两块面板，问「调的是哪个接口」。

## 1. 接口是 `GET /v1/pub/serviceStatus`

三条独立证据：

1. 厂商前端 `p__SystemOverview` 里状态面板的请求是 `w.mCV({})`；
2. `umi.d08aa716.js` 的导出表：`mCV: function(){ return dn }`，而
   `function dn(e){var t={url:"/v1/pub/serviceStatus",method:"get",params:{},data:{}};return (0,i.W)(t)}`；
3. 后端反编译 `PublicController.java:2430` —— `@GetMapping("/serviceStatus")`，
   `@Operation(summary="获取页面概览服务状态信息")`，返回 `ServiceStatusInfo`。

`paas-core` 侧**早已实现**（`PublicExtraController` 的 `/serviceStatus` →
`IPubServiceStatusService.serviceStatusInfo()`）。本轮之前用 `grep` 得到"没实现"的结论是**假阴性**——
本机 `grep` 在该仓库偶发静默返回空，改用 ripgrep 才看到。**结论：查仓库事实一律用 Grep 工具，不要用 shell grep。**

## 2. 三块面板的字段（实测响应）

```json
{"sysinfo":{"k8s":{"version":"1.14.3","status":"Running"},"dns":{...},
            "monitor":{"version":"v0.34.0","status":null},
            "apiserver":{...},"etcd":{...}},
 "appstatus":{"deployments":[...],"pods":[...]},
 "radondbInfo":null}
```

| 区块 | 字段 | 说明 |
| --- | --- | --- |
| 左列 k8s/dns/logging/monitor/apiserver/etcd | `sysinfo` | 厂商**只 put 了 5 个键**：`loggingName` 声明后从未 `infoHashMap.put`（`PublicController:2469-2470`，死代码） |
| 中间 框架 5 项 | `appstatus.pods` | 前端从 **pod 名**推导服务标识；清单里缺的补 `phase:"error"` 占位 |
| 右侧 基础服务 5 项 | `radondbInfo` | 只有 `<radondb.name>-radondb-mysql-` 的 StatefulSet 存在时才填充 |

## 3. A-13：`sysinfo` 键名是厂商缺陷，不复制

`ServiceStatusInfo.java:18` 的 `sysInfo` 只标了 **Gson** 的 `@SerializedName("sysinfo")`，
**没有 `@JsonProperty`**；Spring MVC 用 Jackson 序列化 ⇒ 实际输出 `sysInfo`。
而厂商前端读的是 `t.data?.sysinfo`（小写）⇒ **永远读不到**，回落默认值 `te.Uw`
⇒ 左列恒显示"正常"（连 `status:null` 的 monitor 也是绿的）。

`paas-core` 改为输出小写 `sysinfo`（结论见 `docs/deviations.md` A-13）。
前端同时兼容两种键名（`payload.sysinfo ?? payload.sysInfo`），因为替换期的真实形态是
「自研前端 × 厂商后端」。

## 4. 中间整片"异常"＝两个标签缺失（集群侧，非代码）

后端过滤 Deployment 用 `labels["system/appName"] == "gaop"`（`:2480-2489`），
而本集群 11 个 Deployment **一个都没打**该标签 ⇒ `appstatus` 恒空。
再往里，`pods` 的匹配是 `labels["name"] == <deployment 名>`（`:2532-2538`），
厂商 Pod 只有 `app` 标签、**没有 `name`** ⇒ 即使 Deployment 打了标签，`pods` 仍为空。

修复后实测 `deployments: 9, pods: 9`，全部 `Running`。改动前存档在
`paas-web/deploy/cluster/backup/*.before-podname-label.yaml`。

`radondbInfo` 仍为 `null`——本环境是普通 MySQL Deployment，没有 RadonDB MySQL，属**可解释的空**。

## 5. 单元测试

`paas-core-domain/src/test/java/com/csm/paas/core/domain/node/PubServiceStatusTest.java`（8 条）锁：

- 顶层键名与顺序 `appstatus → sysinfo → radondbInfo`，且**不再输出**驼峰 `sysInfo`；
- `sysinfo` 只有厂商那 5 个键、**没有 logging**；
- 命不中 pod 时 `status` 保持 `null`（不是 `"error"`）；命中且 ready ⇒ `Running`；
- Deployment 只收 `system/appName == "gaop"`；pods 只收带 `name` 标签的；
- 没有 RadonDB pod ⇒ `radondbInfo` 为 null；
- 列 Deployment 抛 `K8sApiException` ⇒ 返回厂商那个"半成品"（`appstatus`/`radondbInfo` 都 null，`sysinfo` 仍在）。
