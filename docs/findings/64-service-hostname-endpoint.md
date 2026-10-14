# `PUT /v1/service/hostName`：实现 + **独立 namespace 端到端验证** + 一处对拉纠错

**证据等级**：〔运行实测〕（独立 namespace e2e + 对拉纠错）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-hn`，用完即删）

造两个 Deployment：`hn-empty`（无 hostname）、`hn-set`（hostname=`old-host`）。后端以
`GAOP_K8S_NAMESPACE=paas-verify-hn` 启动，只指向该 namespace。

| 用例 | 结果 |
| --- | --- |
| A：请求 hostName 为空、现有也为空 | HTTP 200 `data:true`，**hostname 保持为空**（**静默 no-op 生效**）✅ |
| B：请求 `myhost`、现有为空 | HTTP 200 `data:true`，hostname **真的变成 `myhost`** ✅ |
| B2：现有 `old-host` → 请求 `new-host` | hostname 变成 `new-host` ✅ |
| C：服务不存在 | HTTP **400** `service.not.found.update.host.pod`（见 §3 的纠错）✅ |

**清理**：`delete namespace paas-verify-hn`（NotFound 确认）、临时 NodePort 已删、进程清零。

## 2. 实现里最要紧的一条：**区分「Deployment 不存在」与「hostname 为空」**

厂商是：
```java
try { deployment = readNamespacedDeployment(...); }
catch (ApiException e) { return patchHostNameStatefulSet(form); }   // ← 只有"读不到"才回退
String hostname = deployment.getSpec().getTemplate().getSpec().getHostname();
if (isBlank(form.hostName) && isBlank(hostname)) return ok(true);   // ← 两边都空 → 静默 no-op
```

我第一版把网关方法写成"返回 hostname，null 表示不存在"——**把两件事混成了一件**：
Deployment **存在但没设 hostname** 时也返回 null，于是会错误地回退到 StatefulSet 并最终报"服务不存在"。
**是"独立 namespace 用例 A"把它照出来的**（预期 200 true，实际会 500）。

修法：端口拆成 `deploymentExists(...)` 与 `deploymentHostname(...)` 两个方法。
这条和之前 `edit()` vs `patch(JSON)`、`fixedName` vs `gaop/serviceLabel` 是同一族：
**两个语义接近的东西被合并成一个返回值/一个概念，差异只在某个边界条件下暴露。**

## 3. 对拉纠错：错误契约我猜错了

我先按经验写成 `SystemException("service.not.found.update.host.name")`（500）。
**拿厂商对拉**（用不存在的服务名，不会写任何东西）后实测：

```
vendor: HTTP 400  code=400  msg=更新容器主机名称失败  trace: WrongArgumentException: service.not.found.update.host.pod
```

**状态码（400 不是 500）与键名（`.pod` 不是 `.name`）我都猜错了**，已按实测改。
⇒ 这条再次印证：**错误契约也必须对拉，不能靠"看起来该是这样"**。

## 4. 测试

新增 4 条：两边空白 → 静默 no-op（且**不调用任何 patch**）；Deployment 存在但 hostname 为空 → 走 Deployment
**不回退**；只有 Deployment 不存在才回退 StatefulSet；都不存在 → 厂商键（400）。
全量 **38 类 233 条全绿**（上一轮 229）。

## 5. 服务切片进度

**6/33**（`/v1/service`、`labels`、`updateName`、`gaop`、`serviceAuth`、`hostName`）。
其中 5 个有对拉证据、1 个（`hostName`）有独立 namespace 的端到端证据。
